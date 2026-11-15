package com.nzfrankwu.noteapp.data

import androidx.room.withTransaction
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.PageSize
import com.nzfrankwu.noteapp.domain.PageTemplate
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID

/** Why a notebook change couldn't be made. */
sealed interface NotebookError {
    data object BlankTitle : NotebookError
    data object NotFound : NotebookError
    /** A notebook always keeps at least one page. */
    data object LastPage : NotebookError
    /** A PDF notebook needs at least one page, every page a positive size, and an unused ID. */
    data object BadPdf : NotebookError
}


sealed interface NotebookResult {
    /** [id] is the notebook's (or, for page changes, the page's) ID. */
    data class Success(val id: String) : NotebookResult
    data class Failure(val error: NotebookError) : NotebookResult
}

/**
 * Notebooks and their pages (IDEA.md §2.5): creating, renaming, deleting (kept, just hidden) and
 * adding or removing pages. The handwriting on each page is saved separately, by NotebookFiles.
 *
 * Nothing here ever removes a row: deleting sets `deleted_at`, so Undo and Phase 6's "Recently
 * deleted" can bring things back.
 */
class NotebookRepository(
    private val db: NoteAppDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.notebookDao()
    private val folderDao = db.folderDao()

    /** The folder's notebooks (not deleted), most recently changed first. Updates live. */
    fun observeInFolder(folderId: String): Flow<List<NotebookListItem>> = dao.observeInFolder(folderId)

    /** One notebook (deleted or not), updating live. */
    fun observeNotebook(notebookId: String): Flow<Notebook?> = dao.observeNotebook(notebookId)

    /** The notebook's pages in order (not deleted), updating live. */
    fun observePages(notebookId: String): Flow<List<Page>> = dao.observePages(notebookId)

    suspend fun getNotebook(notebookId: String): Notebook? = dao.getNotebook(notebookId)

    suspend fun getPages(notebookId: String): List<Page> = dao.getPages(notebookId)

    /** Creates a notebook with one empty page in [folderId]. */
    suspend fun createNotebook(folderId: String, title: String, template: PageTemplate = PageTemplate.DEFAULT): NotebookResult =
        db.withTransaction {
            val cleanTitle = title.trim()
            if (cleanTitle.isEmpty()) return@withTransaction NotebookResult.Failure(NotebookError.BlankTitle)
            folderDao.getById(folderId) ?: return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
            val now = clock.instant()
            val notebook = Notebook(
                id = newId(), folderId = folderId, title = cleanTitle, template = template,
                pageWidth = Notebook.A4_WIDTH, pageHeight = Notebook.A4_HEIGHT, createdAt = now, updatedAt = now,
            )
            dao.insertNotebook(notebook)
            dao.insertPage(Page(id = newId(), notebookId = notebook.id, position = 0, createdAt = now, updatedAt = now))
            NotebookResult.Success(notebook.id)
        }

    /**
     * A fresh ID for a notebook that [createPdfNotebook] will make. The PDF is copied into that
     * notebook's folder (NotebookFiles) BEFORE its rows exist, so a notebook never points at a
     * file that isn't there yet; an interrupted import only leaves an unused folder behind.
     */
    fun newNotebookId(): String = newId()

    /**
     * Creates notebook [notebookId] in [folderId] with one page per page of the imported PDF
     * [pdfFile] (a file name in the notebook's folder), in order, each the size in [pageSizes].
     * All in one transaction: either the whole notebook is made, or nothing is.
     *
     * The notebook's own size is the first page's; blank pages added later copy their
     * neighbour's size (see [addPage]). Its template is for those blank pages: PDF pages draw no
     * paper pattern.
     */
    suspend fun createPdfNotebook(
        notebookId: String,
        folderId: String,
        title: String,
        pdfFile: String,
        pageSizes: List<PageSize>,
        template: PageTemplate = PageTemplate.DEFAULT,
    ): NotebookResult = db.withTransaction {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return@withTransaction NotebookResult.Failure(NotebookError.BlankTitle)
        if (pageSizes.isEmpty() || pageSizes.any { !it.isValid } || pdfFile.isBlank() || dao.getNotebook(notebookId) != null) {
            return@withTransaction NotebookResult.Failure(NotebookError.BadPdf)
        }
        folderDao.getById(folderId) ?: return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
        val now = clock.instant()
        val first = pageSizes.first()
        dao.insertNotebook(
            Notebook(
                id = notebookId, folderId = folderId, title = cleanTitle, template = template,
                pageWidth = first.width, pageHeight = first.height, createdAt = now, updatedAt = now,
            ),
        )
        pageSizes.forEachIndexed { index, size ->
            dao.insertPage(
                Page(
                    id = newId(), notebookId = notebookId, position = index, createdAt = now, updatedAt = now,
                    width = size.width, height = size.height, pdfFile = pdfFile, pdfPage = index,
                ),
            )
        }
        NotebookResult.Success(notebookId)
    }

    /** A fresh ID for a page whose file is written before the page is added (see [addPage]). */
    fun newPageId(): String = newId()

    /**
     * Creates notebook [notebookId] in [folderId] with the given pages ([pages]: each page's ID and
     * size), in order, in one transaction. For pictures shared into Noteapp: their page files are
     * written first, under these IDs, so a page never shows before its picture is saved.
     */
    suspend fun createNotebookOfPages(
        notebookId: String,
        folderId: String,
        title: String,
        pages: List<Pair<String, PageSize>>,
        template: PageTemplate = PageTemplate.DEFAULT,
    ): NotebookResult = db.withTransaction {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return@withTransaction NotebookResult.Failure(NotebookError.BlankTitle)
        if (pages.isEmpty() || pages.any { !it.second.isValid } || dao.getNotebook(notebookId) != null) {
            return@withTransaction NotebookResult.Failure(NotebookError.BadPdf)
        }
        folderDao.getById(folderId) ?: return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
        val now = clock.instant()
        val first = pages.first().second
        dao.insertNotebook(
            Notebook(
                id = notebookId, folderId = folderId, title = cleanTitle, template = template,
                pageWidth = first.width, pageHeight = first.height, createdAt = now, updatedAt = now,
            ),
        )
        pages.forEachIndexed { index, (pageId, size) ->
            dao.insertPage(Page(id = pageId, notebookId = notebookId, position = index, createdAt = now, updatedAt = now, width = size.width, height = size.height))
        }
        NotebookResult.Success(notebookId)
    }

    suspend fun rename(notebookId: String, newTitle: String): NotebookResult = db.withTransaction {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isEmpty()) return@withTransaction NotebookResult.Failure(NotebookError.BlankTitle)
        val notebook = dao.getNotebook(notebookId) ?: return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
        if (notebook.title != cleanTitle) dao.updateNotebook(notebook.copy(title = cleanTitle, updatedAt = clock.instant()))
        NotebookResult.Success(notebookId)
    }

    /** Changes the paper pattern of every page that doesn't have its own. */
    suspend fun setTemplate(notebookId: String, template: PageTemplate): NotebookResult = db.withTransaction {
        val notebook = dao.getNotebook(notebookId) ?: return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
        if (notebook.template != template) dao.updateNotebook(notebook.copy(template = template, updatedAt = clock.instant()))
        NotebookResult.Success(notebookId)
    }

    /**
     * Deletes the notebook: it's hidden from its folder, but every page and stroke is kept, and
     * [restore] brings it back.
     */
    suspend fun delete(notebookId: String): NotebookResult = setDeleted(notebookId, deleted = true)

    /** Brings back a deleted notebook (e.g. Undo). */
    suspend fun restore(notebookId: String): NotebookResult = setDeleted(notebookId, deleted = false)

    private suspend fun setDeleted(notebookId: String, deleted: Boolean): NotebookResult = db.withTransaction {
        val notebook = dao.getNotebook(notebookId) ?: return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
        if ((notebook.deletedAt != null) != deleted) {
            val now = clock.instant()
            dao.updateNotebook(notebook.copy(deletedAt = if (deleted) now else null, updatedAt = now))
        }
        NotebookResult.Success(notebookId)
    }

    /**
     * Adds an empty page at [index] (0 = first; past the end = last) and returns its ID. The pages
     * after it move along by one.
     *
     * The new page is the same size as the page before it (or, at the front, the page after it),
     * so a blank page among a PDF's pages matches them, unless [size] is given (e.g. a page shaped
     * like a picture). It's always a plain page, never a PDF page.
     */
    suspend fun addPage(notebookId: String, index: Int, size: PageSize? = null, pageId: String? = null): NotebookResult = db.withTransaction {
        val notebook = dao.getNotebook(notebookId) ?: return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
        val now = clock.instant()
        val pages = dao.getPages(notebookId)
        val at = index.coerceIn(0, pages.size)
        val neighbour = pages.getOrNull(at - 1) ?: pages.getOrNull(at)
        if (pageId != null && dao.getPage(pageId) != null) return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
        val page = Page(
            id = pageId ?: newId(), notebookId = notebookId, position = at, createdAt = now, updatedAt = now,
            width = size?.width ?: neighbour?.width, height = size?.height ?: neighbour?.height,
        )
        val renumbered = pages.toMutableList().apply { add(at, page) }
        dao.insertPage(page)
        renumber(renumbered.filter { it.id != page.id }, renumbered, now)
        dao.updateNotebook(notebook.copy(updatedAt = now))
        NotebookResult.Success(page.id)
    }

    /**
     * Deletes a page: it's hidden, but its strokes are kept and [restorePage] brings it back at
     * its old place. A notebook's last page can't be deleted.
     */
    suspend fun deletePage(pageId: String): NotebookResult = db.withTransaction {
        val page = dao.getPage(pageId) ?: return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
        if (page.deletedAt != null) return@withTransaction NotebookResult.Success(pageId)
        val pages = dao.getPages(page.notebookId)
        if (pages.size <= 1) return@withTransaction NotebookResult.Failure(NotebookError.LastPage)
        val now = clock.instant()
        dao.updatePage(page.copy(deletedAt = now, updatedAt = now))
        val remaining = pages.filter { it.id != pageId }
        renumber(remaining, remaining, now)
        touchNotebook(page.notebookId, now)
        NotebookResult.Success(pageId)
    }

    /** Brings back a deleted page at [index] (where it was, for Undo). */
    suspend fun restorePage(pageId: String, index: Int): NotebookResult = db.withTransaction {
        val page = dao.getPage(pageId) ?: return@withTransaction NotebookResult.Failure(NotebookError.NotFound)
        val now = clock.instant()
        val pages = dao.getPages(page.notebookId).filter { it.id != pageId }
        val at = index.coerceIn(0, pages.size)
        val restored = page.copy(deletedAt = null, position = at, updatedAt = now)
        val ordered = pages.toMutableList().apply { add(at, restored) }
        dao.updatePage(restored)
        renumber(ordered.filter { it.id != pageId }, ordered, now)
        touchNotebook(page.notebookId, now)
        NotebookResult.Success(pageId)
    }

    /** Marks that [pageId]'s strokes were just saved, so backups know the page changed. */
    suspend fun markPageEdited(pageId: String) = db.withTransaction {
        val page = dao.getPage(pageId) ?: return@withTransaction
        val now = clock.instant()
        dao.updatePage(page.copy(updatedAt = now))
        touchNotebook(page.notebookId, now)
    }

    private suspend fun touchNotebook(notebookId: String, now: java.time.Instant) {
        dao.getNotebook(notebookId)?.let { dao.updateNotebook(it.copy(updatedAt = now)) }
    }

    /** Saves positions 0, 1, 2… following [order] for the [toUpdate] pages whose position changed. */
    private suspend fun renumber(toUpdate: List<Page>, order: List<Page>, now: java.time.Instant) {
        val positions = order.withIndex().associate { (i, p) -> p.id to i }
        val changed = toUpdate.filter { positions.getValue(it.id) != it.position }
            .map { it.copy(position = positions.getValue(it.id), updatedAt = now) }
        if (changed.isNotEmpty()) dao.updatePages(changed)
    }
}
