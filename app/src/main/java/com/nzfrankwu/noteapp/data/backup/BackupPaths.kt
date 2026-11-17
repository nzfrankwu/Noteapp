package com.nzfrankwu.noteapp.data.backup

/**
 * Which files belong in a backup, and where each one lives in a backup set (PHASE-6.md, "What's
 * in a backup set"). Paths in a backup set always use '/' and are one of:
 * - `db/noteapp.db`: the database (one consistent copy, see [LocalSnapshot])
 * - `files/notebooks/<notebookId>/...`: page files, imported PDFs, `images/`, `bookmarks/`
 * - `files/datastore/<name>.preferences_pb`: the pen and view settings
 *
 * **Never** in a backup (IDEA.md §2.9, §3.5): the Canvas settings and the in-app browser's
 * cookies, the shared-files inbox, caches, reminder alarms and the backup's own settings.
 */
object BackupPaths {
    const val MANIFEST = "manifest.json"
    const val DATABASE = "db/noteapp.db"
    const val FILES_PREFIX = "files/"
    const val NOTEBOOKS_PREFIX = "files/notebooks/"
    const val DATASTORE_PREFIX = "files/datastore/"

    /** The settings stores that are backed up. `canvas_settings` and `backup_settings` never are. */
    val SETTINGS_FILES = listOf("ink_settings.preferences_pb", "view_settings.preferences_pb")

    /** Temporary files of a save or copy that hasn't finished: never backed up. */
    private val UNFINISHED_SUFFIXES = listOf(".saving", ".writing", ".tmp")

    /**
     * True if the file at [relative] (a path inside the app's files folder, like
     * `notebooks/abc/p1.ink`) belongs in a backup.
     */
    fun includeFromFilesDir(relative: String): Boolean {
        val path = relative.replace('\\', '/')
        if (!isSafe(path)) return false
        if (path.startsWith("notebooks/")) {
            val name = path.substringAfterLast('/')
            return path.count { it == '/' } >= 2 && UNFINISHED_SUFFIXES.none { name.endsWith(it) }
        }
        return path.startsWith("datastore/") && path.removePrefix("datastore/") in SETTINGS_FILES
    }

    /**
     * True if [path] may appear in a backup set we restore from. Anything else in a zip (or a
     * manifest) is refused, so a damaged or tampered export can never write outside the app's
     * own notebooks, settings and database (no "../" tricks).
     */
    fun isRestorable(path: String): Boolean {
        if (!isSafe(path)) return false
        if (path == MANIFEST || path == DATABASE) return true
        if (path.startsWith(FILES_PREFIX)) return includeFromFilesDir(path.removePrefix(FILES_PREFIX))
        return false
    }

    /** No empty parts, no "." or "..", no backslashes or drive letters, only plain characters. */
    private fun isSafe(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/") || path.contains('\\') || path.contains(':')) return false
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return false
        return path.all { it.isLetterOrDigit() || it in "-_./" }
    }
}
