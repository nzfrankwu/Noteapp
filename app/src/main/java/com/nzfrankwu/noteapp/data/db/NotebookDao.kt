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
}
