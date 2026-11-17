package com.nzfrankwu.noteapp.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * Puts a checked backup set in place of the app's data (PHASE-6.md, "Restore") 🔺.
 *
 * The set is first unpacked or downloaded into [staging] and checked. [markReady] then writes a
 * marker, and the app restarts. At the very start of the next launch, before anything opens the
 * database or the settings, [finishPending] does the swap in two phases:
 * 1. **Move out:** the current database, notebook files and settings move to
 *    `before-restore/<time>/` (nothing is deleted; the user can remove that copy in Settings).
 * 2. **Move in:** the staged files move into place.
 *
 * Every step is a rename inside the app's own storage, and the marker records which phase is
 * done, so a crash or power cut part-way is simply finished on the next start. If moving out
 * fails, everything moved so far goes back and the restore is cancelled.
 */
class RestoreSwap(
    /** The app's private data folder (holds `files/` and `databases/`). */
    private val dataDir: File,
    private val filesDir: File = File(dataDir, "files"),
    private val databaseFile: File = File(File(dataDir, "databases"), "noteapp.db"),
    /** Cached tile pictures, cleared after a restore (the restored notebooks look different). */
    private val thumbnailsDir: File? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val staging = File(dataDir, "restore-staging")
    private val marker = File(dataDir, "restore-ready.json")
    val beforeRoot = File(dataDir, "before-restore")

    /** True if a checked set waits to be swapped in at the next start. */
    val isPending: Boolean get() = marker.isFile

    /** Records that [staging] holds a checked backup set, made at [backupCreatedAt]. */
    fun markReady(backupCreatedAt: Long) {
        writeMarker(Marker(phase = PHASE_MOVE_OUT, stamp = now(), backupCreatedAt = backupCreatedAt))
    }

    /** Forgets a checked set the user decided not to restore. */
    fun cancel() {
        if (isPending) return // too late: it's swapped in at the next start
        staging.deleteRecursively()
    }

    /**
     * Finishes a restore if one is waiting. Call it before anything opens the database or the
     * settings. Never throws.
     */
    fun finishPending(): Outcome {
        val m = readMarker() ?: run {
            if (!isPending) return Outcome.Nothing
            // A marker nobody can read: never guess, and don't swap anything.
            marker.delete()
            staging.deleteRecursively()
            return Outcome.Failed("The restore couldn't be finished, so nothing was changed.")
        }
        val before = File(beforeRoot, m.stamp.toString())
        val items = items()
        if (m.phase == PHASE_MOVE_OUT) {
            val moved = ArrayList<Pair<File, File>>()
            for (item in items) {
                for (live in item.liveFiles) {
                    if (!live.exists()) continue
                    val target = freeName(File(before, live.relativeTo(dataDir).path))
                    target.parentFile?.mkdirs()
                    if (!live.renameTo(target)) {
                        // Put back what moved, and leave the data as it was.
                        for ((from, to) in moved.asReversed()) to.renameTo(from)
                        marker.delete()
                        staging.deleteRecursively()
                        return Outcome.Failed("The restore couldn't be finished, so nothing was changed.")
                    }
                    moved += live to target
                }
            }
            writeMarker(m.copy(phase = PHASE_MOVE_IN))
        }
        for (item in items) {
            if (!item.staged.exists()) continue // already moved in (or not in this backup)
            val live = item.liveFiles.first()
            if (live.exists()) {
                // Shouldn't happen (it was moved out); keep it rather than lose it.
                val spare = freeName(File(before, live.relativeTo(dataDir).path))
                spare.parentFile?.mkdirs()
                live.renameTo(spare)
            }
            live.parentFile?.mkdirs()
            if (!item.staged.renameTo(live)) return Outcome.Failed("The restore will be finished the next time Noteapp starts.")
        }
        File(before, RESTORED_NOTE).writeText(json.encodeToString(RestoredNote.serializer(), RestoredNote(m.stamp, m.backupCreatedAt)))
        thumbnailsDir?.deleteRecursively()
        staging.deleteRecursively()
        marker.delete()
        return Outcome.Restored(m.backupCreatedAt)
    }

    /** Copies kept from before a restore, newest first. */
    fun beforeRestoreCopies(): List<KeptCopy> =
        beforeRoot.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
            val note = runCatching { json.decodeFromString(RestoredNote.serializer(), File(dir, RESTORED_NOTE).readText()) }.getOrNull()
            KeptCopy(dir, restoredAt = note?.restoredAt ?: dir.lastModified(), size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
        }.sortedByDescending { it.restoredAt }

    /** Deletes a copy kept from before a restore, for good. */
    fun deleteCopy(copy: KeptCopy) {
        require(copy.dir.parentFile == beforeRoot) { "Not a kept copy" }
        copy.dir.deleteRecursively()
    }

    /** One thing that gets swapped: its live file(s) (first = where the staged one goes) and its staged copy. */
    private class Item(val liveFiles: List<File>, val staged: File)

    private fun items(): List<Item> {
        val dbSides = listOf("-wal", "-shm", "-journal").map { File(databaseFile.path + it) }
        return buildList {
            add(Item(listOf(databaseFile) + dbSides, File(staging, BackupPaths.DATABASE)))
            add(Item(listOf(File(filesDir, "notebooks")), File(staging, BackupPaths.NOTEBOOKS_PREFIX.removeSuffix("/"))))
            for (name in BackupPaths.SETTINGS_FILES) {
                add(Item(listOf(File(filesDir, "datastore/$name")), File(staging, BackupPaths.DATASTORE_PREFIX + name)))
            }
        }
    }

    private fun freeName(file: File): File {
        if (!file.exists()) return file
        var n = 2
        while (true) {
            val candidate = File(file.path + ".$n")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    private fun readMarker(): Marker? =
        if (!marker.isFile) null else runCatching { json.decodeFromString(Marker.serializer(), marker.readText()) }.getOrNull()

    /** Written to a temporary file, flushed, then renamed, so the marker is never half written. */
    private fun writeMarker(m: Marker) {
        dataDir.mkdirs()
        val temp = File(marker.path + ".saving")
        FileOutputStream(temp).use { out ->
            out.write(json.encodeToString(Marker.serializer(), m).toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (!temp.renameTo(marker)) {
            marker.delete()
            check(temp.renameTo(marker)) { "Can't write the restore marker" }
        }
    }

    sealed interface Outcome {
        data object Nothing : Outcome
        data class Restored(val backupCreatedAt: Long) : Outcome
        data class Failed(val message: String) : Outcome
    }

    data class KeptCopy(val dir: File, val restoredAt: Long, val size: Long)

    @Serializable
    private data class Marker(val phase: Int, val stamp: Long, val backupCreatedAt: Long)

    @Serializable
    private data class RestoredNote(val restoredAt: Long, val backupCreatedAt: Long)

    private companion object {
        const val PHASE_MOVE_OUT = 1
        const val PHASE_MOVE_IN = 2
        const val RESTORED_NOTE = "restored.json"
        val json = Json { ignoreUnknownKeys = true }
    }
}
