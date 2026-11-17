package com.nzfrankwu.noteapp.data

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.ui.deleted.RecentlyDeletedScreen
import com.nzfrankwu.noteapp.ui.deleted.RecentlyDeletedViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** PHASE-6.md 6.4: Recently deleted, restoring, "Delete now" and the 30-day purge. */
@RunWith(AndroidJUnit4::class)
class RecentlyDeletedTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "deleted-test")
    private var now = Instant.parse("2026-11-17T10:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }
    private lateinit var db: NoteAppDatabase
    private lateinit var folders: FolderRepository
    private lateinit var notebooks: NotebookRepository
    private lateinit var files: NotebookFiles
    private lateinit var purge: NotebookPurge
    private lateinit var folderId: String

    @Before
    fun setUp() = runBlocking {
        root.deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        folders = FolderRepository(db, clock)
        notebooks = NotebookRepository(db, clock)
        files = NotebookFiles(root)
        purge = NotebookPurge(db, files, clock)
        folderId = (folders.createFolder("Electromagnetics", null) as FolderResult.Success).folderId
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private suspend fun notebookWithFiles(title: String, inFolder: String = folderId): String {
        val id = (notebooks.createNotebook(inFolder, title) as NotebookResult.Success).id
        val page = notebooks.getPages(id).first().id
        files.write(id, page, emptyList())
        files.writeAsset(id, "images/a.png", ByteArrayInputStream(byteArrayOf(1, 2)))
        return id
    }

    private fun notebookDir(id: String) = File(root, "notebooks/$id")

    @Test
    fun deleted_isListed_withItsFolder_thenRestoredToIt() = runBlocking {
        val id = notebookWithFiles("Lecture 1")
        notebooks.delete(id)
        val listed = notebooks.observeDeleted().first()
        assertEquals(listOf(id), listed.map { it.notebook.id })
        assertEquals("Electromagnetics", listed[0].folderName)
        assertEquals(1, notebooks.observeDeletedCount().first())

        assertTrue(notebooks.hasFolder(id))
        assertTrue(notebooks.restoreFromDeleted(id) is NotebookResult.Success)
        assertTrue(notebooks.observeDeleted().first().isEmpty())
        assertEquals(folderId, notebooks.getNotebook(id)!!.folderId)
        assertNull(notebooks.getNotebook(id)!!.deletedAt)
    }

    @Test
    fun aNotebookWhoseFolderIsGone_needsAFolderToComeBack() = runBlocking {
        val other = (folders.createFolder("Tutorials", null) as FolderResult.Success).folderId
        val id = notebookWithFiles("Lecture 1")
        notebooks.delete(id)
        assertTrue(folders.deleteFolder(folderId) is FolderResult.Success) // only deleted notebooks in it
        assertFalse(notebooks.hasFolder(id))
        assertNull(notebooks.observeDeleted().first()[0].folderName)
        assertTrue(notebooks.restoreFromDeleted(id) is NotebookResult.Failure)
        assertTrue(notebooks.restoreFromDeleted(id, other) is NotebookResult.Success)
        assertEquals(other, notebooks.getNotebook(id)!!.folderId)
    }

    @Test
    fun deleteNow_removesTheRows_andTheFiles_butOnlyForDeletedNotebooks() = runBlocking {
        val keep = notebookWithFiles("Keep me")
        val gone = notebookWithFiles("Delete me")
        assertFalse("a notebook not in Recently deleted is never removed", purge.deleteForGood(keep))
        assertTrue(notebookDir(keep).exists())

        notebooks.delete(gone)
        assertTrue(purge.deleteForGood(gone))
        assertNull(notebooks.getNotebook(gone))
        assertTrue(db.notebookDao().getAllPagesIncludingDeleted(gone).isEmpty())
        assertFalse(notebookDir(gone).exists())
        assertTrue(notebookDir(keep).exists())
    }

    @Test
    fun purgeExpired_keepsThingsFor30Days() = runBlocking {
        val old = notebookWithFiles("Old")
        val recent = notebookWithFiles("Recent")
        val live = notebookWithFiles("Live")
        val extraPage = (notebooks.addPage(live, 1) as NotebookResult.Success).id
        files.write(live, extraPage, emptyList())

        notebooks.delete(old)
        notebooks.deletePage(extraPage)
        now = now.plus(Duration.ofDays(20))
        notebooks.delete(recent)
        now = now.plus(Duration.ofDays(11)) // old: 31 days; recent: 11 days

        assertEquals(1 to 1, purge.purgeExpired())
        assertNull(notebooks.getNotebook(old))
        assertFalse(notebookDir(old).exists())
        assertTrue(notebooks.getNotebook(recent) != null)
        assertTrue(notebookDir(recent).exists())
        assertNull(db.notebookDao().getPage(extraPage))
        assertFalse(files.pageFile(live, extraPage).exists())
        assertEquals(1, notebooks.getPages(live).size) // the live notebook's other page stays
    }

    @Test
    fun screen_restoresATappedNotebook() {
        val id = runBlocking { notebookWithFiles("Lecture 1").also { notebooks.delete(it) } }
        val vm = RecentlyDeletedViewModel(notebooks, folders, purge)
        compose.setContent { NoteAppTheme { RecentlyDeletedScreen(vm, onBack = {}, clock = clock) } }
        compose.onNodeWithText("30 days left").assertExists()
        compose.onNodeWithTag("tile-Lecture 1").performClick()
        compose.onNodeWithTag("deleted-restore").performClick()
        compose.waitUntil(5_000) { runBlocking { notebooks.getNotebook(id)!!.deletedAt == null } }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("deleted-empty").assertExists() }.isSuccess }
    }

    @Test
    fun screen_deleteNow_asksFirst() {
        val id = runBlocking { notebookWithFiles("Lecture 1").also { notebooks.delete(it) } }
        val vm = RecentlyDeletedViewModel(notebooks, folders, purge)
        compose.setContent { NoteAppTheme { RecentlyDeletedScreen(vm, onBack = {}, clock = clock) } }
        compose.onNodeWithTag("tile-Lecture 1").performClick()
        compose.onNodeWithTag("deleted-delete-now").performClick()
        compose.onNodeWithText("Delete “Lecture 1” for good?").assertExists()
        assertTrue(runBlocking { notebooks.getNotebook(id) } != null) // nothing yet
        compose.onNodeWithTag("deleted-confirm-delete").performClick()
        compose.waitUntil(5_000) { runBlocking { notebooks.getNotebook(id) } == null }
    }
}
