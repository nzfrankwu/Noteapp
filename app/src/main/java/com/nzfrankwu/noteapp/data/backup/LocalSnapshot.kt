package com.nzfrankwu.noteapp.data.backup

import com.nzfrankwu.noteapp.data.StorageLock
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One backup set, ready to be zipped or uploaded: its [manifest] and where each file's bytes are.
 * Files that change (the database, page files, settings) are copies in the staging folder; files
 * that never change once written (imported PDFs, pictures, bookmark pictures) are read where they
 * are ([readInPlace]). Call [source] only while [StorageLock.backup] is still held, or while the
 * notebooks of [readInPlace] are pinned ([BackupPins], what the Drive upload does).
 */
class Snapshot(
    val manifest: BackupManifest,
    private val sources: Map<String, File>,
    /** Paths whose bytes are the live files themselves, not copies in the staging folder. */
    val readInPlace: Set<String> = emptySet(),
) {
    fun source(path: String): File = sources[path] ?: error("Not in this snapshot: $path")
}

/**
 * Takes a consistent copy of everything a backup needs (PHASE-6.md, "Consistent snapshot").
 *
 * While [StorageLock.pages] is held (so no page is half-saved):
 * 1. A write transaction blocks other database writers; the database and its `-wal` side file
 *    are copied byte for byte. Readers carry on.
 * 2. Every page file and the two settings files are copied.
 *
 * Then the copy is made self-contained and checked ([DatabaseCheck.finishCopy]), and every file
 * is hashed (write-once files through [md5Cache], so an unchanged PDF isn't read again).
 *
 * The caller holds [StorageLock.backup] while taking it, and either keeps holding it while using
 * the snapshot, or pins its [Snapshot.readInPlace] notebooks first ([BackupPins], Phase 7, 7.2).
 */
class LocalSnapshot(
    private val database: NoteAppDatabase,
    /** The live database file (`databases/noteapp.db`). */
    private val databaseFile: File,
    /** The app's private files folder (holds `notebooks/` and `datastore/`). */
    private val filesDir: File,
    private val appVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
    /** Remembers write-once files' md5s between backups; null = hash everything every time. */
    private val md5Cache: Md5Cache? = null,
) {
    suspend fun take(staging: File): Snapshot = withContext(Dispatchers.IO) {
        check(StorageLock.backup.isLocked) { "Hold StorageLock.backup while taking and using a snapshot" }
        staging.deleteRecursively()
        staging.mkdirs()
        val sources = LinkedHashMap<String, File>()
        val dbCopy = File(staging, BackupPaths.DATABASE)
        val createdAt: Long
        StorageLock.pages.withLock {
            createdAt = now()
            copyDatabase(dbCopy)
            sources[BackupPaths.DATABASE] = dbCopy
            collectFiles(staging, sources)
        }
        val info = DatabaseCheck.finishCopy(dbCopy)
        val entries = sources.map { (path, file) ->
            val writeOnce = path.startsWith(BackupPaths.FILES_PREFIX) && !changesInPlace(file)
            val md5 = if (writeOnce && md5Cache != null) md5Cache.md5(path, file) else Md5.of(file)
            BackupEntry(path, file.length(), md5)
        }.sortedBy { it.path }
        md5Cache?.save(sources.keys)
        Snapshot(
            BackupManifest(
                createdAt = createdAt,
                appVersion = appVersion,
                databaseVersion = info.version,
                counts = info.counts,
                files = entries,
            ),
            sources,
            readInPlace = sources.filterValues { !it.startsWith(staging) }.keys,
        )
    }

    private fun copyDatabase(target: File) {
        target.parentFile?.mkdirs()
        val sqlite = database.openHelper.writableDatabase
        // Make sure the database exists and is open, then block writers while copying.
        sqlite.beginTransaction()
        try {
            databaseFile.copyTo(target, overwrite = true)
            val wal = File(databaseFile.path + "-wal")
            val walCopy = File(target.path + "-wal")
            if (wal.exists()) wal.copyTo(walCopy, overwrite = true) else walCopy.delete()
        } finally {
            sqlite.endTransaction() // nothing was written: this just lets writers go on
        }
    }

    /** Page files and settings are copied; files that never change are listed where they are. */
    private fun collectFiles(staging: File, sources: MutableMap<String, File>) {
        val notebooks = File(filesDir, "notebooks")
        notebooks.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(filesDir).invariantSeparatorsPath
            if (!BackupPaths.includeFromFilesDir(relative)) return@forEach
            val path = BackupPaths.FILES_PREFIX + relative
            sources[path] = if (changesInPlace(file)) copy(file, File(staging, path)) else file
        }
        for (name in BackupPaths.SETTINGS_FILES) {
            val file = File(filesDir, "datastore/$name")
            if (!file.isFile) continue
            val path = BackupPaths.DATASTORE_PREFIX + name
            sources[path] = copy(file, File(staging, path))
        }
    }

    private fun copy(from: File, to: File): File {
        to.parentFile?.mkdirs()
        from.copyTo(to, overwrite = true)
        return to
    }

    companion object {
        /** Page files are rewritten on every save; every other notebook file is written once. */
        fun changesInPlace(file: File): Boolean = file.name.endsWith(com.nzfrankwu.noteapp.data.NotebookFiles.EXTENSION)
    }
}
