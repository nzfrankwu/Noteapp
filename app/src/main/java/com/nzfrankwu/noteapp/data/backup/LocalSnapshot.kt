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
 * that never change once written (imported PDFs, pictures, bookmark pictures) are read where they are.
 * Call [source] only while [StorageLock.backup] is still held.
 */
class Snapshot(val manifest: BackupManifest, private val sources: Map<String, File>) {
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
 * is hashed. The caller must hold [StorageLock.backup] for as long as it uses the snapshot.
 */
class LocalSnapshot(
    private val database: NoteAppDatabase,
    /** The live database file (`databases/noteapp.db`). */
    private val databaseFile: File,
    /** The app's private files folder (holds `notebooks/` and `datastore/`). */
    private val filesDir: File,
    private val appVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
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
        val entries = sources.map { (path, file) -> BackupEntry(path, file.length(), Md5.of(file)) }.sortedBy { it.path }
        Snapshot(
            BackupManifest(
                createdAt = createdAt,
                appVersion = appVersion,
                databaseVersion = info.version,
                counts = info.counts,
                files = entries,
            ),
            sources,
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
