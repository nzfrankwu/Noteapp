package com.nzfrankwu.noteapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Notebooks and their pages. Deleted ones (deleted_at set) are left out unless a query says so. */
@Dao
interface NotebookDao {
    @Insert
    suspend fun insertNotebook(notebook: Notebook)

    @Update
    suspend fun updateNotebook(notebook: Notebook)

    @Insert
    suspend fun insertPage(page: Page)

    @Update
    suspend fun updatePage(page: Page)

    @Update
    suspend fun updatePages(pages: List<Page>)

    /** A notebook by ID, deleted or not. */
    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebook(id: String): Notebook?

    /** One notebook, updating live (deleted or not; null if it doesn't exist). */
    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun observeNotebook(id: String): Flow<Notebook?>

    /** The folder's notebooks (not deleted), most recently changed first, with their page counts. */
    @Query(
        """
        SELECT notebooks.*,
            (SELECT COUNT(*) FROM pages WHERE pages.notebook_id = notebooks.id AND pages.deleted_at IS NULL)
            AS page_count,
            EXISTS (SELECT 1 FROM pages WHERE pages.notebook_id = notebooks.id AND pages.pdf_file IS NOT NULL
                AND pages.deleted_at IS NULL)
            AS is_pdf
        FROM notebooks
        WHERE notebooks.folder_id = :folderId AND notebooks.deleted_at IS NULL
        ORDER BY notebooks.updated_at DESC, notebooks.title COLLATE NOCASE
        """,
    )
    fun observeInFolder(folderId: String): Flow<List<NotebookListItem>>

    /** Every notebook (not deleted) with its page count, updating live: for the recent panel (PHASE-5.md 5.7). */
    @Query(
        """
        SELECT notebooks.*,
            (SELECT COUNT(*) FROM pages WHERE pages.notebook_id = notebooks.id AND pages.deleted_at IS NULL)
            AS page_count,
            EXISTS (SELECT 1 FROM pages WHERE pages.notebook_id = notebooks.id AND pages.pdf_file IS NOT NULL
                AND pages.deleted_at IS NULL)
            AS is_pdf
        FROM notebooks
        WHERE notebooks.deleted_at IS NULL
        """,
    )
    fun observeAllListItems(): Flow<List<NotebookListItem>>

    /** Every notebook (not deleted), A–Z, updating live: for "Add to Noteapp". */
    @Query("SELECT * FROM notebooks WHERE deleted_at IS NULL ORDER BY title COLLATE NOCASE")
    fun observeAllActive(): Flow<List<Notebook>>

    /** How many notebooks (not deleted) are in the folder. */
    @Query("SELECT COUNT(*) FROM notebooks WHERE folder_id = :folderId AND deleted_at IS NULL")
    suspend fun countInFolder(folderId: String): Int

    /** A page by ID, deleted or not. */
    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPage(id: String): Page?

    /** The notebook's pages (not deleted), in order. */
    @Query("SELECT * FROM pages WHERE notebook_id = :notebookId AND deleted_at IS NULL ORDER BY position, created_at")
    suspend fun getPages(notebookId: String): List<Page>

    /** The notebook's pages (not deleted), in order, updating live. */
    @Query("SELECT * FROM pages WHERE notebook_id = :notebookId AND deleted_at IS NULL ORDER BY position, created_at")
    fun observePages(notebookId: String): Flow<List<Page>>

    /** Every page of the notebook, deleted ones too (for backups and file clean-up). */
    @Query("SELECT * FROM pages WHERE notebook_id = :notebookId ORDER BY position, created_at")
    suspend fun getAllPagesIncludingDeleted(notebookId: String): List<Page>

    /** Recently deleted (PHASE-6.md 6.4): deleted notebooks, most recently deleted first, with their old folder's name. */
    @Query(
        """
        SELECT notebooks.*,
            (SELECT COUNT(*) FROM pages WHERE pages.notebook_id = notebooks.id AND pages.deleted_at IS NULL)
            AS page_count,
            EXISTS (SELECT 1 FROM pages WHERE pages.notebook_id = notebooks.id AND pages.pdf_file IS NOT NULL
                AND pages.deleted_at IS NULL)
            AS is_pdf,
            (SELECT folders.name FROM folders WHERE folders.id = notebooks.folder_id) AS folder_name
        FROM notebooks
        WHERE notebooks.deleted_at IS NOT NULL
        ORDER BY notebooks.deleted_at DESC, notebooks.title COLLATE NOCASE
        """,
    )
    fun observeDeleted(): Flow<List<DeletedNotebook>>

    /** How many notebooks are in Recently deleted, updating live. */
    /** Every notebook, deleted ones too (0 = a fresh app, so restoring a backup is offered). */
    @Query("SELECT COUNT(*) FROM notebooks")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM notebooks WHERE deleted_at IS NOT NULL")
    fun observeDeletedCount(): Flow<Int>

    /** Deleted notebooks deleted before [cutoff] (the 30-day purge). */
    @Query("SELECT id FROM notebooks WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun getDeletedBefore(cutoff: java.time.Instant): List<String>

    /**
     * Removes a deleted notebook's row for good; its pages, task links and bookmarks go with it
     * (foreign keys, ON DELETE CASCADE). Only ever a notebook already in Recently deleted.
     */
    @Query("DELETE FROM notebooks WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun deleteForGood(id: String): Int

    /** Pages deleted before [cutoff] in notebooks that aren't deleted themselves (the 30-day purge). */
    @Query(
        """
        SELECT pages.* FROM pages JOIN notebooks ON notebooks.id = pages.notebook_id
        WHERE pages.deleted_at IS NOT NULL AND pages.deleted_at < :cutoff AND notebooks.deleted_at IS NULL
        """,
    )
    suspend fun getDeletedPagesBefore(cutoff: java.time.Instant): List<Page>

    /** Removes a deleted page's row for good (its bookmarks go with it). Only ever a deleted page. */
    @Query("DELETE FROM pages WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun deletePageForGood(id: String): Int

    /** Every notebook's ID, deleted or not (the clean-up of unused files). */
    @Query("SELECT id FROM notebooks")
    suspend fun getAllIds(): List<String>

    /** The PDF files any page of the notebook shows, deleted pages too (the clean-up of unused files). */
    @Query("SELECT DISTINCT pdf_file FROM pages WHERE notebook_id = :notebookId AND pdf_file IS NOT NULL")
    suspend fun getPdfFiles(notebookId: String): List<String>

    /** The bookmark pen pictures of the notebook's bookmarks (the clean-up of unused files). */
    @Query("SELECT image FROM task_bookmarks WHERE notebook_id = :notebookId AND image IS NOT NULL")
    suspend fun getBookmarkImages(notebookId: String): List<String>
}
