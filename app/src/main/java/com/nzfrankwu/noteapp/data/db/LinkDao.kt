package com.nzfrankwu.noteapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Links between tasks and notebooks, and page bookmarks (Phase 4). Lists leave out deleted
 * (hidden) notebooks and pages: their rows stay, so Undo and "Recently deleted" bring them back
 * with their links.
 */
@Dao
interface LinkDao {
    /** Adds a link; a link that already exists is left as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: TaskNotebookLink)

    @Query("DELETE FROM task_notebook_links WHERE task_id = :taskId AND notebook_id = :notebookId")
    suspend fun deleteLink(taskId: String, notebookId: String): Int

    @Query("SELECT * FROM task_notebook_links WHERE task_id = :taskId AND notebook_id = :notebookId")
    suspend fun getLink(taskId: String, notebookId: String): TaskNotebookLink?

    /** Every link of the task, hidden notebooks too (for Undo). */
    @Query("SELECT * FROM task_notebook_links WHERE task_id = :taskId")
    suspend fun getLinksForTask(taskId: String): List<TaskNotebookLink>

    /** The task's linked notebooks (not deleted), in the order they were linked, with page counts. */
    @Query(
        """
        SELECT notebooks.*,
            (SELECT COUNT(*) FROM pages WHERE pages.notebook_id = notebooks.id AND pages.deleted_at IS NULL)
            AS page_count,
            EXISTS (SELECT 1 FROM pages WHERE pages.notebook_id = notebooks.id AND pages.pdf_file IS NOT NULL
                AND pages.deleted_at IS NULL)
            AS is_pdf
        FROM task_notebook_links AS links JOIN notebooks ON notebooks.id = links.notebook_id
        WHERE links.task_id = :taskId AND notebooks.deleted_at IS NULL
        ORDER BY links.created_at, notebooks.title COLLATE NOCASE
        """,
    )
    fun observeNotebooksForTask(taskId: String): Flow<List<NotebookListItem>>

    /** The notebook's linked tasks with their course codes, in the order they were linked. */
    @Query(
        """
        SELECT tasks.*, courses.code AS course_code, courses.archived_at AS course_archived_at
        FROM task_notebook_links AS links
            JOIN tasks ON tasks.id = links.task_id
            LEFT JOIN courses ON courses.id = tasks.course_id
        WHERE links.notebook_id = :notebookId
        ORDER BY links.created_at, tasks.title COLLATE NOCASE
        """,
    )
    fun observeTasksForNotebook(notebookId: String): Flow<List<TaskWithCourse>>

    /** Every link to a notebook that isn't deleted, with the notebook's title (task card chips). */
    @Query(
        """
        SELECT links.task_id, links.notebook_id, notebooks.title
        FROM task_notebook_links AS links JOIN notebooks ON notebooks.id = links.notebook_id
        WHERE notebooks.deleted_at IS NULL
        ORDER BY links.created_at, notebooks.title COLLATE NOCASE
        """,
    )
    fun observeAllLinks(): Flow<List<LinkedNotebook>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBookmark(bookmark: TaskBookmark)

    @Update
    suspend fun updateBookmark(bookmark: TaskBookmark)

    @Query("DELETE FROM task_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String): Int

    @Query("SELECT * FROM task_bookmarks WHERE id = :id")
    suspend fun getBookmark(id: String): TaskBookmark?

    /** Every bookmark of the task, on hidden pages too (for Undo). */
    @Query("SELECT * FROM task_bookmarks WHERE task_id = :taskId")
    suspend fun getBookmarksForTask(taskId: String): List<TaskBookmark>

    /** The task's bookmarks in one notebook, on hidden pages too (for unlinking with Undo). */
    @Query("SELECT * FROM task_bookmarks WHERE task_id = :taskId AND notebook_id = :notebookId")
    suspend fun getBookmarks(taskId: String, notebookId: String): List<TaskBookmark>

    @Query("DELETE FROM task_bookmarks WHERE task_id = :taskId AND notebook_id = :notebookId")
    suspend fun deleteBookmarks(taskId: String, notebookId: String)

    /**
     * The task's bookmarks on pages and notebooks that aren't deleted, each with its notebook's
     * title and the page's number now (its place among the notebook's pages). Unsorted: the order
     * is worked out in domain/Bookmarks.kt.
     */
    @Query(
        """
        SELECT task_bookmarks.*, notebooks.title AS notebook_title,
            (SELECT COUNT(*) FROM pages AS earlier
                WHERE earlier.notebook_id = pages.notebook_id AND earlier.deleted_at IS NULL
                AND (earlier.position < pages.position
                    OR (earlier.position = pages.position AND earlier.created_at < pages.created_at)
                    OR (earlier.position = pages.position AND earlier.created_at = pages.created_at AND earlier.id < pages.id))
            ) + 1 AS page_number
        FROM task_bookmarks
            JOIN pages ON pages.id = task_bookmarks.page_id
            JOIN notebooks ON notebooks.id = task_bookmarks.notebook_id
        WHERE task_bookmarks.task_id = :taskId AND pages.deleted_at IS NULL AND notebooks.deleted_at IS NULL
        """,
    )
    fun observeBookmarksForTask(taskId: String): Flow<List<BookmarkWithPage>>
}
