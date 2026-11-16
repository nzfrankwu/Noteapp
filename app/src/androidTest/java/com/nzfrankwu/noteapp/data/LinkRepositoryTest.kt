package com.nzfrankwu.noteapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.PageSpot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Links between tasks and notebooks, page bookmarks, and what happens when things are deleted (Phase 4.1). */
@RunWith(AndroidJUnit4::class)
class LinkRepositoryTest {
    private lateinit var db: NoteAppDatabase
    private lateinit var links: LinkRepository
    private lateinit var notebooks: NotebookRepository
    private lateinit var tasks: TaskRepository
    private lateinit var folderId: String
    private lateinit var courseId: String
    private var now = Instant.parse("2026-11-16T10:00:00Z")
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
        val ids = { "id-${++nextId}" }
        folderId = (FolderRepository(db, clock, ids).createFolder("ELECTENG 204", "ELECTENG 204") as FolderResult.Success).folderId
        courseId = checkNotNull(db.folderDao().getById(folderId)?.courseId)
        notebooks = NotebookRepository(db, clock, ids)
        tasks = TaskRepository(db, clock, ids)
        links = LinkRepository(db, notebooks, clock, ids)
    }

    @After
    fun tearDown() = db.close()

    private fun later() { now = now.plusSeconds(60) }

    private suspend fun task(title: String = "Assignment 1") =
        (tasks.createTask(TaskDraft(title, courseId)) as TaskResult.Success).taskId

    private suspend fun notebook(title: String = "Lecture 1", pages: Int = 1): String {
        val id = (notebooks.createNotebook(folderId, title) as NotebookResult.Success).id
        repeat(pages - 1) { notebooks.addPage(id, Int.MAX_VALUE) }
        return id
    }

    private suspend fun bookmark(taskId: String, pageId: String, note: String? = null, spot: PageSpot? = null) =
        (links.addBookmark(taskId, pageId, note, spot) as LinkResult.Success).id

    @Test
    fun link_showsBothWays_andLinkingTwiceIsHarmless() = runBlocking {
        val t = task()
        val n = notebook()
        assertEquals(LinkResult.Success(n), links.link(t, n))
        later()
        assertEquals(LinkResult.Success(n), links.link(t, n))

        assertEquals(listOf(n), links.observeNotebooksForTask(t).first().map { it.notebook.id })
        assertEquals(1, links.observeNotebooksForTask(t).first().single().pageCount)
        assertEquals(listOf(t), links.observeTasksForNotebook(n).first().map { it.task.id })
        assertEquals("ELECTENG 204", links.observeTasksForNotebook(n).first().single().courseCode)
        assertEquals(mapOf(t to listOf(n)), links.observeAllLinks().first().mapValues { e -> e.value.map { it.notebookId } })
    }

    @Test
    fun aNotebookCanHaveManyTasks_andATaskManyNotebooks() = runBlocking {
        val t1 = task("A1")
        val t2 = task("A2")
        val n1 = notebook("N1")
        val n2 = notebook("N2")
        links.link(t1, n1); later()
        links.link(t1, n2); later()
        links.link(t2, n1)
        assertEquals(listOf(n1, n2), links.observeNotebooksForTask(t1).first().map { it.notebook.id })
        assertEquals(listOf(t1, t2), links.observeTasksForNotebook(n1).first().map { it.task.id })
    }

    @Test
    fun linkingSomethingMissing_fails() = runBlocking {
        val t = task()
        val n = notebook()
        assertEquals(LinkResult.Failure(LinkError.TaskNotFound), links.link("nope", n))
        assertEquals(LinkResult.Failure(LinkError.NotebookNotFound), links.link(t, "nope"))
        notebooks.delete(n)
        assertEquals(LinkResult.Failure(LinkError.NotebookNotFound), links.link(t, n))
    }

    @Test
    fun deletedNotebooks_areHidden_andComeBackWithTheirLinks() = runBlocking {
        val t = task()
        val n = notebook()
        links.link(t, n)
        notebooks.delete(n)
        assertTrue(links.observeNotebooksForTask(t).first().isEmpty())
        assertTrue(links.observeAllLinks().first().isEmpty())
        notebooks.restore(n)
        assertEquals(listOf(n), links.observeNotebooksForTask(t).first().map { it.notebook.id })
    }

    @Test
    fun unlink_removesTheLinkAndItsBookmarks_andUndoPutsThemBack() = runBlocking {
        val t = task()
        val n = notebook()
        val page = notebooks.getPages(n).single().id
        val b = bookmark(t, page, "see Q2")
        val unlinked = checkNotNull(links.unlink(t, n))
        assertEquals(1, unlinked.bookmarks.size)
        assertTrue(links.observeNotebooksForTask(t).first().isEmpty())
        assertTrue(links.observeBookmarksForTask(t).first().isEmpty())
        assertNull(links.unlink(t, n))

        links.restoreUnlinked(unlinked)
        assertEquals(listOf(n), links.observeNotebooksForTask(t).first().map { it.notebook.id })
        assertEquals(listOf(b), links.observeBookmarksForTask(t).first().flatMap { g -> g.bookmarks.map { it.id } })
    }

    @Test
    fun bookmarking_linksTheNotebook_andKeepsTheNoteAndSpot() = runBlocking {
        val t = task()
        val n = notebook(pages = 3)
        val page3 = notebooks.getPages(n)[2].id
        val spot = PageSpot(10f, 200f, 300f, 40f)
        val b = bookmark(t, page3, "  Equation 4  ", spot)

        assertEquals(listOf(n), links.observeNotebooksForTask(t).first().map { it.notebook.id })
        val entry = links.observeBookmarksForTask(t).first().single().bookmarks.single()
        assertEquals(b, entry.id)
        assertEquals(3, entry.pageNumber)
        assertEquals("Equation 4", entry.note)
        assertEquals(spot, entry.spot)
        assertEquals(page3, entry.pageId)
    }

    @Test
    fun bookmarks_areGroupedByNotebook_inPageOrder_andFollowPageChanges() = runBlocking {
        val t = task()
        val lecture = notebook("Lecture", pages = 4)
        val alpha = notebook("Alpha notes", pages = 2)
        val lp = notebooks.getPages(lecture).map { it.id }
        val ap = notebooks.getPages(alpha).map { it.id }
        bookmark(t, lp[3], "four")
        bookmark(t, lp[1], "two")
        bookmark(t, ap[1], "alpha two")

        val groups = links.observeBookmarksForTask(t).first()
        assertEquals(listOf("Alpha notes", "Lecture"), groups.map { it.notebookTitle })
        assertEquals(listOf(2, 4), groups[1].bookmarks.map { it.pageNumber })

        // Deleting page 1 moves the others up; deleting a bookmarked page hides its bookmark.
        notebooks.deletePage(lp[0])
        assertEquals(listOf(1, 3), links.observeBookmarksForTask(t).first()[1].bookmarks.map { it.pageNumber })
        notebooks.deletePage(lp[1])
        assertEquals(listOf("four"), links.observeBookmarksForTask(t).first()[1].bookmarks.map { it.note })
        notebooks.restorePage(lp[1], 0)
        assertEquals(listOf("two", "four"), links.observeBookmarksForTask(t).first()[1].bookmarks.map { it.note })
    }

    @Test
    fun bookmarkingADeletedPage_fails() = runBlocking {
        val t = task()
        val n = notebook(pages = 2)
        val page = notebooks.getPages(n)[1].id
        notebooks.deletePage(page)
        assertEquals(LinkResult.Failure(LinkError.PageNotFound), links.addBookmark(t, page, null))
        assertTrue(links.observeNotebooksForTask(t).first().isEmpty())
    }

    @Test
    fun notes_canBeChangedAndCleared_andBookmarksDeletedWithUndo() = runBlocking {
        val t = task()
        val n = notebook()
        val b = bookmark(t, notebooks.getPages(n).single().id)
        assertNull(links.observeBookmarksForTask(t).first().single().bookmarks.single().note)
        assertTrue(links.setNote(b, " check units "))
        assertEquals("check units", links.observeBookmarksForTask(t).first().single().bookmarks.single().note)
        assertTrue(links.setNote(b, "   "))
        assertNull(links.observeBookmarksForTask(t).first().single().bookmarks.single().note)

        val deleted = checkNotNull(links.deleteBookmark(b))
        assertTrue(links.observeBookmarksForTask(t).first().isEmpty())
        assertEquals("The notebook stays linked", 1, links.observeNotebooksForTask(t).first().size)
        links.restoreBookmark(deleted)
        assertEquals(b, links.observeBookmarksForTask(t).first().single().bookmarks.single().id)
    }

    @Test
    fun deletingATask_andUndo_bringsBackItsLinksAndBookmarks() = runBlocking {
        val t = task()
        val n1 = notebook("N1")
        val n2 = notebook("N2")
        links.link(t, n1)
        val b = bookmark(t, notebooks.getPages(n2).single().id, "note", PageSpot(1f, 2f, 3f, 4f))

        val deleted = tasks.deleteTask(t) as DeleteTaskResult.Deleted
        assertEquals(2, deleted.links.size)
        assertEquals(1, deleted.bookmarks.size)
        // The database removed them with the task.
        assertTrue(links.observeAllLinks().first().isEmpty())
        assertTrue(links.observeTasksForNotebook(n1).first().isEmpty())

        tasks.restoreTask(deleted)
        assertEquals(setOf(n1, n2), links.observeNotebooksForTask(t).first().map { it.notebook.id }.toSet())
        val entry = links.observeBookmarksForTask(t).first().single().bookmarks.single()
        assertEquals(b, entry.id)
        assertEquals(PageSpot(1f, 2f, 3f, 4f), entry.spot)
    }

    @Test
    fun undoingATaskDelete_skipsNotebooksThatAreGoneForGood() = runBlocking {
        val t = task()
        val n = notebook()
        links.link(t, n)
        bookmark(t, notebooks.getPages(n).single().id)
        val deleted = tasks.deleteTask(t) as DeleteTaskResult.Deleted
        // Phase 6's "empty Recently deleted" removes rows for good; simulate it.
        db.openHelper.writableDatabase.execSQL("DELETE FROM notebooks WHERE id = '$n'")

        tasks.restoreTask(deleted)
        assertNotNull(tasks.getTask(t))
        assertTrue(links.observeNotebooksForTask(t).first().isEmpty())
        assertTrue(links.observeBookmarksForTask(t).first().isEmpty())
    }

    @Test
    fun aNotebookRemovedForGood_takesItsLinksAndBookmarksWithIt() = runBlocking {
        val t = task()
        val n = notebook()
        bookmark(t, notebooks.getPages(n).single().id)
        db.openHelper.writableDatabase.execSQL("DELETE FROM notebooks WHERE id = '$n'")
        assertTrue(db.linkDao().getLinksForTask(t).isEmpty())
        assertTrue(db.linkDao().getBookmarksForTask(t).isEmpty())
        assertNotNull("The task itself stays", tasks.getTask(t))
    }

    @Test
    fun newNotebookForTask_goesInTheCourseFolder_andIsLinked() = runBlocking {
        val t = task()
        val made = links.createNotebookForTask(t, "Assignment 1 working") as LinkResult.Success
        val nb = checkNotNull(notebooks.getNotebook(made.id))
        assertEquals(folderId, nb.folderId)
        assertEquals(listOf(made.id), links.observeNotebooksForTask(t).first().map { it.notebook.id })
        assertEquals(LinkResult.Failure(LinkError.BlankTitle), links.createNotebookForTask(t, "  "))
    }

    @Test
    fun newNotebookForATaskWithoutACourseFolder_asksForAFolder() = runBlocking {
        val other = (FolderRepository(db).createFolder("Misc", null) as FolderResult.Success).folderId
        db.openHelper.writableDatabase.execSQL("UPDATE folders SET course_id = NULL WHERE id = '$folderId'")
        val t = task()
        assertEquals(LinkResult.Failure(LinkError.NoCourseFolder), links.createNotebookForTask(t, "Working"))
        val made = links.createNotebookForTask(t, "Working", folderId = other) as LinkResult.Success
        assertEquals(other, notebooks.getNotebook(made.id)?.folderId)
    }
}
