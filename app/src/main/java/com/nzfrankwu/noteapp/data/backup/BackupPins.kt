package com.nzfrankwu.noteapp.data.backup

/**
 * Notebooks whose files a Drive upload is still reading where they are (Phase 7, 7.2).
 *
 * A Drive backup holds [com.nzfrankwu.noteapp.data.StorageLock.backup] only while it takes its
 * snapshot, then uploads without it, so Export, Delete now, the purge and the clean-up don't wait
 * half an hour. Its snapshot has its own copies of the database and page files, but imported PDFs
 * and pictures (written once, never changed) are read in place. So the snapshot pins their
 * notebooks (while it still holds the lock) until the upload ends, and anything that deletes
 * notebook files for good leaves a pinned notebook's files for a later run.
 *
 * (Android doesn't let apps make hard links, and copying every PDF on every backup would be slow.)
 */
object BackupPins {
    private val pins = HashMap<String, Int>() // notebook id → how many runs pinned it

    /** Pins the notebooks of these backup paths (`files/notebooks/<id>/…`); returns what to [unpin]. */
    @Synchronized
    fun pin(paths: Collection<String>): Set<String> {
        val ids = paths.mapNotNull(::notebookOf).toSet()
        for (id in ids) pins[id] = (pins[id] ?: 0) + 1
        return ids
    }

    @Synchronized
    fun unpin(ids: Set<String>) {
        for (id in ids) {
            val left = (pins[id] ?: 0) - 1
            if (left > 0) pins[id] = left else pins.remove(id)
        }
    }

    /** True while an upload may still read this notebook's files: don't delete them now. */
    @Synchronized
    fun isPinned(notebookId: String): Boolean = notebookId in pins

    /** The notebook a backup path belongs to, or null for files outside `files/notebooks/`. */
    fun notebookOf(path: String): String? {
        val prefix = BackupPaths.FILES_PREFIX + "notebooks/"
        if (!path.startsWith(prefix)) return null
        return path.removePrefix(prefix).substringBefore('/', missingDelimiterValue = "").ifEmpty { null }
    }
}
