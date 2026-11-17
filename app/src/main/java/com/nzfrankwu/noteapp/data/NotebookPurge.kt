package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration

/**
 * Deletes things for good (PHASE-6.md 6.4): a notebook from Recently deleted ("Delete now", or 30
 * days after it was deleted), and pages deleted more than 30 days ago.
 *
 * The rows go first, then the files. If the files can't all be removed (or the app stops halfway),
 * what's left is just unused files, which the clean-up of unused files removes later; never the
 * other way round (rows pointing at missing files). Holds [StorageLock.backup], so a snapshot or export in
 * progress never loses files under it.
 */
class NotebookPurge(
    private val db: NoteAppDatabase,
    private val files: NotebookFiles,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val dao = db.notebookDao()

    /** Deletes a notebook that is in Recently deleted, for good. False if it isn't there. */
    suspend fun deleteForGood(notebookId: String): Boolean = StorageLock.backup.withLock {
        val removed = dao.deleteForGood(notebookId) > 0
        if (removed) deleteFiles(notebookId)
        removed
    }

    /** Removes notebooks and pages deleted more than [KEEP] ago. Returns how many of each. */
    suspend fun purgeExpired(): Pair<Int, Int> = StorageLock.backup.withLock {
        val cutoff = clock.instant().minus(KEEP)
        var notebooks = 0
        for (id in dao.getDeletedBefore(cutoff)) {
            if (dao.deleteForGood(id) > 0) {
                deleteFiles(id)
                notebooks++
            }
        }
        var pages = 0
        for (page in dao.getDeletedPagesBefore(cutoff)) {
            if (dao.deletePageForGood(page.id) > 0) {
                withContext(Dispatchers.IO) { files.deletePageFile(page.notebookId, page.id) }
                pages++
            }
        }
        notebooks to pages
    }

    /**
     * A notebook a Drive upload still reads keeps its files for now ([com.nzfrankwu.noteapp.data.backup.BackupPins]);
     * with its rows gone they're unused files, which the clean-up removes later.
     */
    private suspend fun deleteFiles(notebookId: String) {
        if (com.nzfrankwu.noteapp.data.backup.BackupPins.isPinned(notebookId)) return
        withContext(Dispatchers.IO) { files.deleteNotebookFiles(notebookId) }
    }

    companion object {
        /** How long Recently deleted keeps things (IDEA.md §2.9). */
        val KEEP: Duration = Duration.ofDays(30)
    }
}
