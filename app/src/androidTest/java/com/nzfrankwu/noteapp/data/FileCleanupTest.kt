package com.nzfrankwu.noteapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.ink.InkImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** PHASE-6.md 6.5: only old files that nothing uses are removed. */
@RunWith(AndroidJUnit4::class)
class FileCleanupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "cleanup-test")
    private val now = Instant.parse("2026-11-17T10:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }
    private val old = now.minus(Duration.ofDays(40)).toEpochMilli()
    private val recent = now.minus(Duration.ofDays(3)).toEpochMilli()
    private lateinit var db: NoteAppDatabase
    private lateinit var notebooks: NotebookRepository
    private lateinit var files: NotebookFiles
    private var busy = emptySet<String>()
    private lateinit var cleanup: FileCleanup
    private lateinit var nb: String
    private lateinit var page: String

    @Before
    fun setUp() = runBlocking {
        root.deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val folder = (FolderRepository(db).createFolder("Electromagnetics", null) as FolderResult.Success).folderId
        notebooks = NotebookRepository(db)
        files = NotebookFiles(root)
        cleanup = FileCleanup(db, root, inUse = { busy }, clock = clock)
        nb = (notebooks.createNotebook(folder, "Lecture 1") as NotebookResult.Success).id
        page = notebooks.getPages(nb).first().id
        files.write(nb, page, emptyList(), images = listOf(InkImage("i1", "images/used.png", 0f, 0f, 10f, 10f)))
        asset("images/used.png", old)
        asset("images/unused-old.png", old)
        asset("images/unused-new.png", recent)
        asset("bookmarks/unused-old.png", old)
        asset("pdf-unused-old.pdf", old)
        asset("images/half.png.writing", old)
        files.pageFile(nb, page).setLastModified(old)
        Unit
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun asset(name: String, modified: Long): File =
        files.assetFile(nb, name).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)); setLastModified(modified) }

    private fun exists(name: String) = files.assetFile(nb, name).exists()

    @Test
    fun oldUnusedFiles_areRemoved_andEverythingUsedOrRecentStays() = runBlocking {
        val result = cleanup.run()
        assertTrue(exists("images/used.png"))
        assertTrue(files.pageFile(nb, page).exists())
        assertTrue("too recent to remove", exists("images/unused-new.png"))
        assertFalse(exists("images/unused-old.png"))
        assertFalse(exists("bookmarks/unused-old.png"))
        assertFalse(exists("pdf-unused-old.pdf"))
        assertFalse(exists("images/half.png.writing"))
        assertEquals(4, result.files)
    }

    @Test
    fun aPictureOnADeletedPage_stillCounts_asUsed() = runBlocking {
        val second = (notebooks.addPage(nb, 1) as NotebookResult.Success).id
        files.write(nb, second, emptyList(), images = listOf(InkImage("i2", "images/unused-old.png", 0f, 0f, 10f, 10f)))
        notebooks.deletePage(second)
        cleanup.run()
        assertTrue(exists("images/unused-old.png"))
    }

    @Test
    fun anOpenNotebook_isLeftAlone() = runBlocking {
        busy = setOf(nb)
        assertEquals(FileCleanup.Result(), cleanup.run())
        assertTrue(exists("images/unused-old.png"))
    }

    @Test
    fun aDamagedPageFile_meansNothingIsRemoved_fromThatNotebook() = runBlocking {
        File(files.pageFile(nb, page).parentFile, "damaged.ink").writeBytes(byteArrayOf(9, 9, 9))
        cleanup.run()
        assertTrue(exists("images/unused-old.png"))
        assertTrue(exists("pdf-unused-old.pdf"))
    }

    @Test
    fun aPdfThatAPageShows_isKept() = runBlocking {
        val pdf = asset("pdf-shown.pdf", old)
        db.notebookDao().updatePage(db.notebookDao().getPage(page)!!.copy(pdfFile = "pdf-shown.pdf", pdfPage = 0))
        cleanup.run()
        assertTrue(pdf.exists())
    }

    @Test
    fun theFolderOfANotebookThatNoLongerExists_goesOnlyWhenOld() = runBlocking {
        val gone = File(root, "notebooks/gone-old").apply { mkdirs() }
        File(gone, "p.ink").apply { writeText("x"); setLastModified(old) }
        val fresh = File(root, "notebooks/importing-now").apply { mkdirs() }
        File(fresh, "pdf-a.pdf").apply { writeText("x"); setLastModified(recent) }
        val result = cleanup.run()
        assertFalse(gone.exists())
        assertTrue("an import still in progress keeps its folder", fresh.exists())
        assertEquals(1, result.folders)
    }
}
