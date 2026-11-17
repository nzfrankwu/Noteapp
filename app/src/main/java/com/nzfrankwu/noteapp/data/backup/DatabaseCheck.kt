package com.nzfrankwu.noteapp.data.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** What a database copy holds, read from the copy itself. */
data class DatabaseInfo(val version: Int, val counts: BackupCounts)

/**
 * Checks a copy of the database (never the live one): SQLite's own integrity check, its version
 * and what it holds.
 */
object DatabaseCheck {
    /**
     * Turns a copied database and its copied `-wal` side file into one self-contained file
     * (`journal_mode=DELETE`), then checks it. Throws [BackupException] if it's damaged.
     */
    fun finishCopy(file: File): DatabaseInfo {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            db.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
            return inspect(db)
        } finally {
            db.close()
            deleteSideFiles(file)
        }
    }

    /** Checks a database file without changing it (a backup about to be restored). */
    fun inspectReadOnly(file: File): DatabaseInfo {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw BackupException("The backup's database can't be opened.", e)
        }
        try {
            return inspect(db)
        } finally {
            db.close()
        }
    }

    /** Removes `-wal`, `-shm` and `-journal` files next to [file]. */
    fun deleteSideFiles(file: File) {
        for (suffix in listOf("-wal", "-shm", "-journal")) File(file.path + suffix).delete()
    }

    private fun inspect(db: SQLiteDatabase): DatabaseInfo {
        try {
            val result = db.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (result != "ok") throw BackupException("The backup's database is damaged ($result).")
            return DatabaseInfo(
                version = db.version,
                counts = BackupCounts(
                    folders = count(db, "SELECT COUNT(*) FROM folders"),
                    notebooks = count(db, "SELECT COUNT(*) FROM notebooks WHERE deleted_at IS NULL"),
                    pages = count(
                        db,
                        "SELECT COUNT(*) FROM pages p JOIN notebooks n ON n.id = p.notebook_id " +
                            "WHERE p.deleted_at IS NULL AND n.deleted_at IS NULL",
                    ),
                    tasks = count(db, "SELECT COUNT(*) FROM tasks"),
                ),
            )
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("The backup's database can't be read.", e)
        }
    }

    /** A count, or 0 if the table doesn't exist (a database older than notebooks, version 3 or below). */
    private fun count(db: SQLiteDatabase, sql: String): Int =
        runCatching { db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }.getOrDefault(0)
}
