package com.nzfrankwu.noteapp.data

import androidx.room.withTransaction
import com.nzfrankwu.noteapp.data.db.LinkedNotebook
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.data.db.TaskBookmark
import com.nzfrankwu.noteapp.data.db.TaskNotebookLink
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.BookmarkEntry
import com.nzfrankwu.noteapp.domain.BookmarkGroup
import com.nzfrankwu.noteapp.domain.Bookmarks
import com.nzfrankwu.noteapp.domain.PageSpot
import com.nzfrankwu.noteapp.domain.PageTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

/** Why a link or bookmark couldn't be made. */
sealed interface LinkError {
    data object TaskNotFound : LinkError
    data object NotebookNotFound : LinkError
    data object PageNotFound : LinkError
    /** The task has no course folder to put a new notebook in: pick a folder. */
    data object NoCourseFolder : LinkError
    data object BlankTitle : LinkError
}

sealed interface LinkResult {
    /** [id]: the notebook's ID (links, new notebooks) or the bookmark's (bookmarks). */
    data class Success(val id: String) : LinkResult
    data class Failure(val error: LinkError) : LinkResult
}

/** What [LinkRepository.unlink] removed, so Undo ([LinkRepository.restoreUnlinked]) can put it back. */
data class Unlinked(val link: TaskNotebookLink, val bookmarks: List<TaskBookmark>)

/**
 * Links between tasks and notebooks, and page bookmarks for tasks (IDEA.md §2.4, PHASE-4.md).
 *
 * A bookmark always comes with a link: bookmarking a page for a task links its notebook too, and
 * unlinking a notebook removes the task's bookmarks in it (Undo brings both back).
 */
class LinkRepository(
    private val db: NoteAppDatabase,
    private val notebooks: NotebookRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.linkDao()
    private val taskDao = db.taskDao()
    private val notebookDao = db.notebookDao()
    private val folderDao = db.folderDao()

    /** The task's linked notebooks (not deleted), in the order they were linked. Updates live. */
    fun observeNotebooksForTask(taskId: String): Flow<List<NotebookListItem>> = dao.observeNotebooksForTask(taskId)

    /** The notebook's linked tasks, in the order they were linked. Updates live. */
    fun observeTasksForNotebook(notebookId: String): Flow<List<TaskWithCourse>> = dao.observeTasksForNotebook(notebookId)

    /** Every task's linked notebooks (not deleted), by task ID, for the chips on task cards. */
    fun observeAllLinks(): Flow<Map<String, List<LinkedNotebook>>> = dao.observeAllLinks().map { links -> links.groupBy { it.taskId } }

    /** The task's bookmarks on pages that aren't deleted, grouped by notebook in page order. */
    fun observeBookmarksForTask(taskId: String): Flow<List<BookmarkGroup>> =
        dao.observeBookmarksForTask(taskId).map { rows ->
            Bookmarks.group(
                rows.map { row ->
                    val b = row.bookmark
                    BookmarkEntry(
                        id = b.id, notebookId = b.notebookId, notebookTitle = row.notebookTitle, pageId = b.pageId,
                        pageNumber = row.pageNumber, note = b.note, spot = b.spot(), createdAt = b.createdAt,
                    )
                },
            )
        }

    /** Links the notebook to the task. Linking twice is fine (nothing changes). */
    suspend fun link(taskId: String, notebookId: String): LinkResult = db.withTransaction {
        taskDao.getById(taskId) ?: return@withTransaction LinkResult.Failure(LinkError.TaskNotFound)
        val notebook = notebookDao.getNotebook(notebookId)
        if (notebook == null || notebook.deletedAt != null) return@withTransaction LinkResult.Failure(LinkError.NotebookNotFound)
        dao.insertLink(TaskNotebookLink(taskId, notebookId, clock.instant()))
        LinkResult.Success(notebookId)
    }

    /**
     * Unlinks the notebook from the task, and removes the task's bookmarks in it. Returns what was
     * removed (for Undo), or null if they weren't linked.
     */
    suspend fun unlink(taskId: String, notebookId: String): Unlinked? = db.withTransaction {
        val link = dao.getLink(taskId, notebookId) ?: return@withTransaction null
        val bookmarks = dao.getBookmarks(taskId, notebookId)
        dao.deleteBookmarks(taskId, notebookId)
        dao.deleteLink(taskId, notebookId)
        Unlinked(link, bookmarks)
    }

    /** Undo for [unlink]: puts the link and its bookmarks back (skipping anything gone for good). */
    suspend fun restoreUnlinked(unlinked: Unlinked) = db.withTransaction {
        val link = unlinked.link
        if (taskDao.getById(link.taskId) == null || notebookDao.getNotebook(link.notebookId) == null) return@withTransaction
        dao.insertLink(link)
        for (b in unlinked.bookmarks) {
            if (notebookDao.getPage(b.pageId) != null && dao.getBookmark(b.id) == null) dao.insertBookmark(b)
        }
    }

    /** The folder a new notebook for the task goes in: its course's folder, or null if it has none. */
    suspend fun courseFolderFor(taskId: String): String? {
        val courseId = taskDao.getById(taskId)?.courseId ?: return null
        return folderDao.getByCourseId(courseId)?.id
    }

    /**
     * Makes a notebook called [title] for the task and links it. It goes in [folderId] if given,
     * else in the task's course folder; with neither, it fails with [LinkError.NoCourseFolder] so
     * the screen can ask for a folder. All in one transaction.
     */
    suspend fun createNotebookForTask(
        taskId: String,
        title: String,
        folderId: String? = null,
        template: PageTemplate = PageTemplate.DEFAULT,
    ): LinkResult = db.withTransaction {
        taskDao.getById(taskId) ?: return@withTransaction LinkResult.Failure(LinkError.TaskNotFound)
        val folder = folderId ?: courseFolderFor(taskId) ?: return@withTransaction LinkResult.Failure(LinkError.NoCourseFolder)
        when (val made = notebooks.createNotebook(folder, title, template)) {
            is NotebookResult.Failure -> LinkResult.Failure(
                if (made.error == NotebookError.BlankTitle) LinkError.BlankTitle else LinkError.NotebookNotFound,
            )
            is NotebookResult.Success -> {
                dao.insertLink(TaskNotebookLink(taskId, made.id, clock.instant()))
                LinkResult.Success(made.id)
            }
        }
    }

    /**
     * Bookmarks page [pageId] for the task, with an optional [note] and [spot] (null = the whole
     * page), and links the page's notebook to the task if it isn't yet. Returns the bookmark's ID.
     */
    suspend fun addBookmark(taskId: String, pageId: String, note: String?, spot: PageSpot? = null): LinkResult =
        db.withTransaction {
            taskDao.getById(taskId) ?: return@withTransaction LinkResult.Failure(LinkError.TaskNotFound)
            val page = notebookDao.getPage(pageId)
            if (page == null || page.deletedAt != null) return@withTransaction LinkResult.Failure(LinkError.PageNotFound)
            val notebook = notebookDao.getNotebook(page.notebookId)
            if (notebook == null || notebook.deletedAt != null) return@withTransaction LinkResult.Failure(LinkError.NotebookNotFound)
            val now = clock.instant()
            dao.insertLink(TaskNotebookLink(taskId, notebook.id, now))
            val keptSpot = spot?.takeIf { it.isValid }
            val bookmark = TaskBookmark(
                id = newId(), taskId = taskId, notebookId = notebook.id, pageId = pageId, note = Bookmarks.cleanNote(note),
                spotLeft = keptSpot?.left, spotTop = keptSpot?.top, spotWidth = keptSpot?.width, spotHeight = keptSpot?.height,
                createdAt = now, updatedAt = now,
            )
            dao.insertBookmark(bookmark)
            LinkResult.Success(bookmark.id)
        }

    /** Adds, changes or (with a blank [note]) clears a bookmark's note. */
    suspend fun setNote(bookmarkId: String, note: String?): Boolean = db.withTransaction {
        val bookmark = dao.getBookmark(bookmarkId) ?: return@withTransaction false
        val clean = Bookmarks.cleanNote(note)
        if (clean != bookmark.note) dao.updateBookmark(bookmark.copy(note = clean, updatedAt = clock.instant()))
        true
    }

    /** Deletes a bookmark and returns it, so the screen can offer Undo ([restoreBookmark]). */
    suspend fun deleteBookmark(bookmarkId: String): TaskBookmark? = db.withTransaction {
        val bookmark = dao.getBookmark(bookmarkId) ?: return@withTransaction null
        dao.deleteBookmark(bookmarkId)
        bookmark
    }

    /** Undo for [deleteBookmark] (skipped if its task or page is gone for good). */
    suspend fun restoreBookmark(bookmark: TaskBookmark) = db.withTransaction {
        if (dao.getBookmark(bookmark.id) != null) return@withTransaction
        if (taskDao.getById(bookmark.taskId) == null || notebookDao.getPage(bookmark.pageId) == null) return@withTransaction
        dao.insertLink(TaskNotebookLink(bookmark.taskId, bookmark.notebookId, clock.instant()))
        dao.insertBookmark(bookmark)
    }

    /** Every folder, for the pickers. */
    fun observeAllFolders(): Flow<List<com.nzfrankwu.noteapp.data.db.Folder>> = folderDao.observeAll()

    /** Every notebook (not deleted), for the pickers. */
    fun observeAllNotebooks(): Flow<List<com.nzfrankwu.noteapp.data.db.Notebook>> = notebookDao.observeAllActive()

    /** Every task with its course, for the task picker. */
    fun observeAllTasks(): Flow<List<TaskWithCourse>> = taskDao.observeAllWithCourse()

    /** Bookmarks on the notebook's pages (not deleted), updating live. */
    fun observeBookmarksInNotebook(notebookId: String): Flow<List<TaskBookmark>> = dao.observeBookmarksInNotebook(notebookId)
}
