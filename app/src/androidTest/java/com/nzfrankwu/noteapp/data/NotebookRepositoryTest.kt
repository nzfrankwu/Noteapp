package com.nzfrankwu.noteapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
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
}
