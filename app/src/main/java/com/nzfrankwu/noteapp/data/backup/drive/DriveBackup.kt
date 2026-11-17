package com.nzfrankwu.noteapp.data.backup.drive

import com.nzfrankwu.noteapp.data.StorageLock
import com.nzfrankwu.noteapp.data.backup.BackupException
import com.nzfrankwu.noteapp.data.backup.BackupManifest
import com.nzfrankwu.noteapp.data.backup.BackupPaths
import com.nzfrankwu.noteapp.data.backup.Md5
import com.nzfrankwu.noteapp.data.backup.Snapshot
import com.nzfrankwu.noteapp.data.backup.StagedSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The full backup to Google Drive (PHASE-6.md 6.9), and reading it back (6.11).
 *
 * ```
 * My Drive/Noteapp/Backup/blobs/<md5>              each file's content once, named by its md5
 * My Drive/Noteapp/Backup/snapshots/<time>.json    a backup set's file list, written LAST
 * ```
 *
 * A snapshot exists only once every file it lists is safely in Drive (Drive's own md5 matched),
 * so a run that stops halfway leaves every earlier snapshot whole. Unchanged files are never
 * uploaded again. Kept: the newest snapshot plus the newest of each of the last 7 days; files no
 * kept snapshot uses go after a day (never sooner, so a run still uploading never loses its files).
 */
class DriveBackup(
    private val drive: DriveApi,
    /** Takes a consistent local snapshot into the folder given (called with [StorageLock.backup] held). */
    private val takeSnapshot: suspend (File) -> Snapshot,
    /** A scratch folder (in the cache); emptied after every run. */
    private val stagingDir: File,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    data class Result(
        val snapshot: RemoteSnapshot,
        val uploadedFiles: Int,
        val uploadedBytes: Long,
        /** What the whole backup takes in Drive (kept snapshots and the files they use). */
        val storedBytes: Long,
        val removedSnapshots: Int,
        val removedFiles: Int,
    )

    /** One backup set in Drive. */
    data class RemoteSnapshot(val fileId: String, val name: String, val createdAt: Long, val size: Long)

    private class Folders(val blobs: DriveFile, val snapshots: DriveFile)

    /** Takes a snapshot and uploads what Drive doesn't have yet. Throws [DriveException] or [BackupException]. */
    suspend fun backUp(onProgress: (String, Float?) -> Unit = { _, _ -> }): Result = withContext(Dispatchers.IO) {
        StorageLock.backup.withLock {
            try {
                onProgress("Preparing the backup…", null)
                val snapshot = takeSnapshot(File(stagingDir, "snapshot"))
                val folders = folders(create = true)!!
                onProgress("Checking Google Drive…", null)
                val blobs = drive.listFiles(folders.blobs.id).filter { it.isGoodBlob() }.associateBy { it.name }

                // 1. Every file Drive doesn't have yet, once per content.
                val missing = snapshot.manifest.files.filter { it.md5 !in blobs }.distinctBy { it.md5 }
                val total = missing.sumOf { it.size }.coerceAtLeast(1)
                var sent = 0L
                for (entry in missing) {
                    onProgress("Backing up…", sent.toFloat() / total)
                    val uploaded = drive.upload(snapshot.source(entry.path), entry.md5, folders.blobs.id, appProperties = mapOf(KIND to BLOB))
                    if (uploaded.md5Checksum != entry.md5 || uploaded.sizeBytes != entry.size) {
                        runCatching { drive.delete(uploaded.id) }
                        throw BackupException("A file changed or got damaged on the way to Google Drive. Noteapp will try again.")
                    }
                    sent += entry.size
                }

                // 2. The snapshot itself, last.
                onProgress("Finishing the backup…", 1f)
                val manifest = snapshot.manifest
                val listFile = File(stagingDir, "snapshot.json").apply { parentFile?.mkdirs(); writeText(manifest.toJson()) }
                val listMd5 = Md5.of(listFile)
                val written = drive.upload(
                    listFile, snapshotName(manifest.createdAt), folders.snapshots.id, "application/json",
                    mapOf(KIND to SNAPSHOT, CREATED_AT to manifest.createdAt.toString()),
                )
                if (written.md5Checksum != listMd5) {
                    runCatching { drive.delete(written.id) }
                    throw BackupException("The backup's file list got damaged on the way to Google Drive. Noteapp will try again.")
                }
                val mine = RemoteSnapshot(written.id, written.name, manifest.createdAt, written.sizeBytes)

                // 3. Tidy up. The backup is already safe, so problems here only wait for the next run.
                val tidy = try {
                    tidy(folders, mine)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                Result(
                    snapshot = mine,
                    uploadedFiles = missing.size,
                    uploadedBytes = missing.sumOf { it.size },
                    storedBytes = tidy?.storedBytes ?: 0L,
                    removedSnapshots = tidy?.removedSnapshots ?: 0,
                    removedFiles = tidy?.removedFiles ?: 0,
                )
            } finally {
                stagingDir.deleteRecursively()
            }
        }
    }

    /** Every backup set in Drive, newest first (empty if there's no backup folder yet). */
    suspend fun snapshots(): List<RemoteSnapshot> = withContext(Dispatchers.IO) {
        val folders = folders(create = false) ?: return@withContext emptyList()
        drive.listFiles(folders.snapshots.id).mapNotNull { it.toSnapshot() }.sortedByDescending { it.createdAt }
    }

    /** Reads one backup set's file list. Throws [BackupException] if it can't be used. */
    suspend fun manifest(snapshot: RemoteSnapshot, scratch: File): BackupManifest = withContext(Dispatchers.IO) {
        val file = File(scratch, "snapshot-${snapshot.fileId}.json")
        try {
            drive.download(snapshot.fileId, file)
            BackupManifest.fromJson(file.readText())
        } finally {
            file.delete()
        }
    }

    /**
     * Downloads every file of [manifest] into [staging] (emptied first) and checks them all
     * ([StagedSet.verify]). The caller holds [StorageLock.backup] and then hands [staging] to
     * the restore swap.
     */
    suspend fun download(
        manifest: BackupManifest,
        staging: File,
        onProgress: (Float) -> Unit = {},
    ): Unit = withContext(Dispatchers.IO) {
        val folders = folders(create = false) ?: throw BackupException("There's no Noteapp backup in this Google Drive.")
        val blobs = drive.listFiles(folders.blobs.id).filter { it.isGoodBlob() }.associateBy { it.name }
        staging.deleteRecursively()
        staging.mkdirs()
        val total = manifest.totalSize.coerceAtLeast(1)
        var done = 0L
        for (entry in manifest.files) {
            onProgress(done.toFloat() / total)
            val blob = blobs[entry.md5] ?: throw BackupException("This backup is missing a file in Google Drive. Try an older one.")
            val target = File(staging, entry.path)
            if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                throw BackupException("This backup holds an unexpected file and can't be used.")
            }
            drive.download(blob.id, target)
            if (target.length() != entry.size || Md5.of(target) != entry.md5) {
                throw BackupException("A file in the Drive backup is damaged (${entry.path.substringAfterLast('/')}). Try an older one.")
            }
            done += entry.size
        }
        File(staging, BackupPaths.MANIFEST).writeText(manifest.toJson())
        StagedSet.verify(staging, manifest)
        onProgress(1f)
    }

    // ---- Tidying up ----

    private class Tidy(val storedBytes: Long, val removedSnapshots: Int, val removedFiles: Int)

    private suspend fun tidy(folders: Folders, mine: RemoteSnapshot): Tidy {
        val all = drive.listFiles(folders.snapshots.id)
        val dated = all.mapNotNull { it.toSnapshot() }
        val keep = Retention.keep(dated, mine, clock).map { it.fileId }.toSet()
        var removedSnapshots = 0
        for (s in dated) {
            if (s.fileId !in keep) {
                drive.delete(s.fileId)
                removedSnapshots++
            }
        }
        // Files nobody can date stay (they might be someone's backup); they count as in use too.
        val remaining = all.filter { it.toSnapshot() == null || it.id in keep }
        val used = HashSet<String>()
        val scratch = File(stagingDir, "lists").apply { mkdirs() }
        for (file in remaining) {
            val listFile = File(scratch, file.id)
            drive.download(file.id, listFile)
            // If any kept snapshot can't be read, nothing is removed: better a full Drive than a broken backup.
            used += BackupManifest.fromJson(listFile.readText()).files.map { it.md5 }
        }
        val cutoff = clock.instant().minus(BLOB_GRACE)
        var removedFiles = 0
        var stored = remaining.sumOf { it.sizeBytes }
        val seen = HashSet<String>()
        for (blob in drive.listFiles(folders.blobs.id)) {
            val needed = blob.isGoodBlob() && blob.name in used && seen.add(blob.name)
            if (needed) {
                stored += blob.sizeBytes
                continue
            }
            val modified = blob.modifiedTime?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (modified != null && modified.isBefore(cutoff)) {
                drive.delete(blob.id)
                removedFiles++
            } else {
                stored += blob.sizeBytes
            }
        }
        return Tidy(stored, removedSnapshots, removedFiles)
    }

    private suspend fun folders(create: Boolean): Folders? {
        suspend fun get(name: String, parent: String?) =
            if (create) drive.folder(name, parent) else drive.findFolder(name, parent)
        val root = get(ROOT_FOLDER, null) ?: return null
        val backup = get(BACKUP_FOLDER, root.id) ?: return null
        val blobs = get(BLOBS_FOLDER, backup.id) ?: return null
        val snapshots = get(SNAPSHOTS_FOLDER, backup.id) ?: return null
        return Folders(blobs, snapshots)
    }

    private fun DriveFile.isGoodBlob() = Md5.isValid(name) && md5Checksum == name

    private fun DriveFile.toSnapshot(): RemoteSnapshot? {
        val createdAt = appProperties[CREATED_AT]?.toLongOrNull() ?: return null
        return RemoteSnapshot(id, name, createdAt, sizeBytes)
    }

    companion object {
        const val ROOT_FOLDER = "Noteapp"
        const val BACKUP_FOLDER = "Backup"
        const val BLOBS_FOLDER = "blobs"
        const val SNAPSHOTS_FOLDER = "snapshots"
        const val KIND = "noteapp"
        const val BLOB = "blob"
        const val SNAPSHOT = "snapshot"
        const val CREATED_AT = "createdAt"

        /** An unused file stays this long, so a backup still uploading (maybe on another device) keeps it. */
        val BLOB_GRACE: Duration = Duration.ofDays(1)

        private val NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss").withZone(ZoneOffset.UTC)

        /** e.g. `2026-11-17 10-00-00 UTC.json`. */
        fun snapshotName(createdAt: Long): String = NAME.format(Instant.ofEpochMilli(createdAt)) + " UTC.json"
    }
}

/** Which snapshots stay (the user's choice, IDEA.md §2.9): the newest, plus the newest of each of the last 7 days. */
object Retention {
    const val DAYS = 7L

    fun keep(snapshots: List<DriveBackup.RemoteSnapshot>, newest: DriveBackup.RemoteSnapshot, clock: Clock): List<DriveBackup.RemoteSnapshot> {
        val zone = clock.zone
        val today = clock.instant().atZone(zone).toLocalDate()
        val firstDay = today.minusDays(DAYS - 1)
        val perDay = snapshots
            .filter { it.fileId != newest.fileId }
            .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .filterKeys { !it.isBefore(firstDay) && !it.isAfter(today) }
            .map { (_, sameDay) -> sameDay.maxBy { it.createdAt } }
        val newestDay = Instant.ofEpochMilli(newest.createdAt).atZone(zone).toLocalDate()
        // The newest stands for its own day; an older one from that day isn't needed as well.
        val others = perDay.filter { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() != newestDay }
        // Anything newer than "mine" (e.g. from another device) is never removed by this run.
        val newer = snapshots.filter { it.createdAt > newest.createdAt }
        return (listOf(newest) + others + newer).distinctBy { it.fileId }
    }
}
