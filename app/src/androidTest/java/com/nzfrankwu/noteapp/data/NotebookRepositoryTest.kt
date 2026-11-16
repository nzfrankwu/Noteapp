package com.nzfrankwu.noteapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.PageSize
import com.nzfrankwu.noteapp.domain.PageTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class NotebookRepositoryTest {
    private lateinit var db: NoteAppDatabase
    private lateinit var repo: NotebookRepository
    private lateinit var folderId: String
    private var now = Instant.parse("2026-11-15T10:00:00Z")
    private var nextId = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val clock = object : Clock() {
            override fun instant() = now
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
        }
        folderId = (FolderRepository(db, clock) { "f-${++nextId}" }.createFolder("ELECTENG 292", null) as FolderResult.Success).folderId
        repo = NotebookRepository(db, clock) { "id-${++nextId}" }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun create(title: String = "Lecture 1", template: PageTemplate = PageTemplate.LINED) =
        (repo.createNotebook(folderId, title, template) as NotebookResult.Success).id

    private suspend fun pageIds(notebookId: String) = repo.getPages(notebookId).map { it.id }

    @Test
    fun newNotebook_isA4_withOneEmptyPage() = runBlocking {
        val id = create("  Lecture 1 ", PageTemplate.GRID)
        val nb = repo.getNotebook(id)!!
        assertEquals("Lecture 1", nb.title)
        assertEquals(PageTemplate.GRID, nb.template)
        assertEquals(Notebook.A4_WIDTH, nb.pageWidth)
        assertEquals(Notebook.A4_HEIGHT, nb.pageHeight)
        val pages = repo.getPages(id)
        assertEquals(1, pages.size)
        assertEquals(0, pages.single().position)
        val tile = repo.observeInFolder(folderId).first().single()
        assertEquals(1, tile.pageCount)
    }

    @Test
    fun newNotebook_needsATitleAndAFolder() = runBlocking {
        assertEquals(NotebookResult.Failure(NotebookError.BlankTitle), repo.createNotebook(folderId, "  "))
        assertEquals(NotebookResult.Failure(NotebookError.NotFound), repo.createNotebook("missing", "x"))
    }

    @Test
    fun notebooks_areListedMostRecentlyChangedFirst() = runBlocking {
        val a = create("A")
        now = now.plusSeconds(60)
        val b = create("B")
        assertEquals(listOf(b, a), repo.observeInFolder(folderId).first().map { it.notebook.id })
        now = now.plusSeconds(60)
        repo.rename(a, "A2")
        assertEquals(listOf(a, b), repo.observeInFolder(folderId).first().map { it.notebook.id })
    }

    @Test
    fun deletedNotebook_isHiddenButKept_andUndoBringsItBack() = runBlocking {
        val id = create()
        repo.delete(id)
        assertEquals(emptyList<Any>(), repo.observeInFolder(folderId).first())
        assertNotNull(repo.getNotebook(id)!!.deletedAt)
        assertEquals(1, repo.getPages(id).size)

        repo.restore(id)
        assertNull(repo.getNotebook(id)!!.deletedAt)
        assertEquals(listOf(id), repo.observeInFolder(folderId).first().map { it.notebook.id })
    }

    @Test
    fun addPage_putsItWhereAsked_andRenumbers() = runBlocking {
        val id = create()
        val first = pageIds(id).single()
        val last = (repo.addPage(id, 99) as NotebookResult.Success).id
        val middle = (repo.addPage(id, 1) as NotebookResult.Success).id
        val front = (repo.addPage(id, 0) as NotebookResult.Success).id
        assertEquals(listOf(front, first, middle, last), pageIds(id))
        assertEquals(listOf(0, 1, 2, 3), repo.getPages(id).map { it.position })
        assertEquals(4, repo.observeInFolder(folderId).first().single().pageCount)
    }

    @Test
    fun deletePage_hidesIt_andRestorePutsItBack() = runBlocking {
        val id = create()
        val p0 = pageIds(id).single()
        val p1 = (repo.addPage(id, 1) as NotebookResult.Success).id
        val p2 = (repo.addPage(id, 2) as NotebookResult.Success).id

        repo.deletePage(p1)
        assertEquals(listOf(p0, p2), pageIds(id))
        assertEquals(listOf(0, 1), repo.getPages(id).map { it.position })
        assertNotNull(db.notebookDao().getPage(p1)!!.deletedAt)

        repo.restorePage(p1, 1)
        assertEquals(listOf(p0, p1, p2), pageIds(id))
        assertEquals(listOf(0, 1, 2), repo.getPages(id).map { it.position })
    }

    @Test
    fun theLastPage_cantBeDeleted() = runBlocking {
        val id = create()
        assertEquals(NotebookResult.Failure(NotebookError.LastPage), repo.deletePage(pageIds(id).single()))
        assertEquals(1, repo.getPages(id).size)
    }

    @Test
    fun editingAPage_marksThePageAndNotebookChanged() = runBlocking {
        val id = create()
        val page = pageIds(id).single()
        now = now.plusSeconds(300)
        repo.markPageEdited(page)
        assertEquals(now, db.notebookDao().getPage(page)!!.updatedAt)
        assertEquals(now, repo.getNotebook(id)!!.updatedAt)
    }

    @Test
    fun setTemplate_changesTheNotebooksPattern() = runBlocking {
        val id = create()
        repo.setTemplate(id, PageTemplate.DOTS)
        assertEquals(PageTemplate.DOTS, repo.getNotebook(id)!!.template)
    }

    // ---- PDF notebooks and page sizes (Phase 3) ----

    private val slides = listOf(PageSize(842f, 595f), PageSize(842f, 595f), PageSize(612f, 792f))

    private fun Page.size() = PageSize(width!!, height!!)

    @Test
    fun pdfNotebook_hasOnePagePerPdfPage_inOrder_withTheirSizes() = runBlocking {
        val id = repo.newNotebookId()
        assertEquals(NotebookResult.Success(id), repo.createPdfNotebook(id, folderId, " Week 1 slides ", "pdf-1.pdf", slides))
        val nb = repo.getNotebook(id)!!
        assertEquals("Week 1 slides", nb.title)
        assertEquals(PageTemplate.LINED, nb.template)
        assertEquals(842f, nb.pageWidth)
        assertEquals(595f, nb.pageHeight)
        val pages = repo.getPages(id)
        assertEquals(listOf(0, 1, 2), pages.map { it.position })
        assertEquals(listOf(0, 1, 2), pages.map { it.pdfPage })
        assertEquals(List(3) { "pdf-1.pdf" }, pages.map { it.pdfFile })
        assertEquals(slides, pages.map { it.size() })
        assertEquals(3, repo.observeInFolder(folderId).first().single().pageCount)
    }

    @Test
    fun pdfNotebook_refusesBadInput_andCreatesNothing() = runBlocking {
        val id = repo.newNotebookId()
        assertEquals(NotebookResult.Failure(NotebookError.BlankTitle), repo.createPdfNotebook(id, folderId, " ", "a.pdf", slides))
        for (bad in listOf(emptyList(), listOf(PageSize(0f, 100f)), listOf(PageSize(Float.NaN, 100f)))) {
            assertEquals(NotebookResult.Failure(NotebookError.BadPdf), repo.createPdfNotebook(id, folderId, "x", "a.pdf", bad))
        }
        assertEquals(NotebookResult.Failure(NotebookError.BadPdf), repo.createPdfNotebook(id, folderId, "x", " ", slides))
        assertEquals(NotebookResult.Failure(NotebookError.NotFound), repo.createPdfNotebook(id, "missing", "x", "a.pdf", slides))
        assertNull(repo.getNotebook(id))
        assertEquals(0, repo.getPages(id).size)
        assertEquals(0, repo.observeInFolder(folderId).first().size)
    }

    @Test
    fun pdfNotebook_cantReuseAnExistingId() = runBlocking {
        val existing = create()
        assertEquals(
            NotebookResult.Failure(NotebookError.BadPdf),
            repo.createPdfNotebook(existing, folderId, "Slides", "a.pdf", slides),
        )
        assertEquals("Lecture 1", repo.getNotebook(existing)!!.title)
        assertEquals(1, repo.getPages(existing).size)
    }

    @Test
    fun addedPage_copiesTheSizeOfThePageBefore_andIsNeverAPdfPage() = runBlocking {
        val id = repo.newNotebookId()
        repo.createPdfNotebook(id, folderId, "Slides", "pdf-1.pdf", slides)
        val atEnd = (repo.addPage(id, 99) as NotebookResult.Success).id
        val afterFirst = (repo.addPage(id, 1) as NotebookResult.Success).id
        val atFront = (repo.addPage(id, 0) as NotebookResult.Success).id
        val pages = repo.getPages(id).associateBy { it.id }
        assertEquals(PageSize(612f, 792f), pages.getValue(atEnd).size())
        assertEquals(PageSize(842f, 595f), pages.getValue(afterFirst).size())
        // At the front there's no page before it, so it copies the page after it.
        assertEquals(PageSize(842f, 595f), pages.getValue(atFront).size())
        for (added in listOf(atEnd, afterFirst, atFront)) {
            assertNull(pages.getValue(added).pdfFile)
            assertNull(pages.getValue(added).pdfPage)
        }
    }

    @Test
    fun pdfPages_canBeInsertedIntoAnyNotebook_inOrder_afterThePageOnScreen() = runBlocking {
        val id = create()
        val first = pageIds(id).single()
        val second = (repo.addPage(id, 1) as NotebookResult.Success).id
        val added = repo.insertPdfPages(id, 1, "pdf-x.pdf", listOf(2 to PageSize(842f, 595f), 4 to PageSize(612f, 792f)))!!
        assertEquals(listOf(first) + added + second, pageIds(id))
        assertEquals(listOf(0, 1, 2, 3), repo.getPages(id).map { it.position })
        val pages = repo.getPages(id)
        assertEquals(listOf(null, 2, 4, null), pages.map { it.pdfPage })
        assertEquals(listOf(null, "pdf-x.pdf", "pdf-x.pdf", null), pages.map { it.pdfFile })
        assertEquals(612f, pages[2].width)
        assertEquals(true, repo.observeInFolder(folderId).first().single().isPdf)
        // Nothing is inserted for bad input.
        assertNull(repo.insertPdfPages(id, 0, "pdf-x.pdf", emptyList()))
        assertNull(repo.insertPdfPages("missing", 0, "pdf-x.pdf", listOf(0 to PageSize(1f, 1f))))
        assertEquals(4, repo.getPages(id).size)
    }

    @Test
    fun anAddedPage_canHaveItsOwnPattern() = runBlocking {
        val id = create(template = PageTemplate.LINED)
        val page = (repo.addPage(id, 1, template = PageTemplate.GRID) as NotebookResult.Success).id
        assertEquals(PageTemplate.GRID, db.notebookDao().getPage(page)!!.template)
    }

    @Test
    fun addedPage_inAPlainNotebook_usesTheNotebooksSize() = runBlocking {
        val id = create()
        val page = db.notebookDao().getPage((repo.addPage(id, 1) as NotebookResult.Success).id)!!
        assertNull(page.width)
        assertNull(page.height)
        assertNull(page.pdfFile)
    }
}
