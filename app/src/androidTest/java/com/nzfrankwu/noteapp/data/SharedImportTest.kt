package com.nzfrankwu.noteapp.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.PageSize
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
import java.io.ByteArrayOutputStream
import java.io.File

/** Pictures and PDFs shared into Noteapp (PHASE-3.md 3.8): the part behind the destination sheet. */
@RunWith(AndroidJUnit4::class)
class SharedImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NoteAppDatabase
    private lateinit var root: File
    private lateinit var files: NotebookFiles
    private lateinit var notebooks: NotebookRepository
    private lateinit var shared: SharedImport
    private lateinit var folderId: String

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        root = File(context.cacheDir, "shared-import-test").apply { deleteRecursively(); mkdirs() }
        files = NotebookFiles(root)
        notebooks = NotebookRepository(db)
        shared = SharedImport(notebooks, files, ImageImport(files), PdfImport(notebooks, files))
        folderId = (FolderRepository(db).createFolder("ELECTENG 204", null) as FolderResult.Success).folderId
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun jpeg(w: Int, h: Int): ByteArray {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test
    fun pictures_becomePagesAtTheEndOfANotebook_eachShapedLikeItsPicture() = runBlocking {
        val id = (notebooks.createNotebook(folderId, "Lecture notes") as NotebookResult.Success).id
        val added = shared.picturesToNotebook(id, listOf({ jpeg(1600, 1200).inputStream() }, { jpeg(600, 1200).inputStream() }, { null }))
        assertEquals(2, added)
        val pages = notebooks.getPages(id)
        assertEquals(3, pages.size)
        assertEquals(PageSize(842f, 631.5f), PageSize(pages[1].width!!, pages[1].height!!))
        assertEquals(PageSize(421f, 842f), PageSize(pages[2].width!!, pages[2].height!!))
        // Each new page's file already holds its picture, filling the page.
        val read = files.read(id, pages[2].id) as PageRead.Ok
        val picture = read.images.single()
        assertEquals(0f, picture.left)
        assertEquals(421f, picture.width)
        assertTrue(files.assetFile(id, picture.asset).exists())
    }

    @Test
    fun pictures_canMakeANewNotebook() = runBlocking {
        val id = shared.picturesToNewNotebook(folderId, "Whiteboard", listOf({ jpeg(800, 600).inputStream() }, { jpeg(800, 600).inputStream() }))
        assertNotNull(id)
        val tile = notebooks.observeInFolder(folderId).first().single()
        assertEquals("Whiteboard", tile.notebook.title)
        assertEquals(2, tile.pageCount)
        assertEquals(842f, tile.notebook.pageWidth)
        for (page in notebooks.getPages(id!!)) assertEquals(1, (files.read(id, page.id) as PageRead.Ok).images.size)
    }

    @Test
    fun noReadablePicture_makesNothing_andLeavesNoFiles() = runBlocking {
        assertNull(shared.picturesToNewNotebook(folderId, "x", listOf({ "junk".byteInputStream() }, { null })))
        assertEquals(0, notebooks.observeInFolder(folderId).first().size)
        assertTrue(File(root, NotebookFiles.DIR).list().isNullOrEmpty())
        assertNull(shared.picturesToNewNotebook("no-such-folder", "x", listOf({ jpeg(10, 10).inputStream() })))
        assertTrue(File(root, NotebookFiles.DIR).list().isNullOrEmpty())
    }

    @Test
    fun theInbox_keepsACopy_evenAfterTheOriginalIsGone() {
        val original = File(root, "outside.jpg").apply { writeBytes(jpeg(20, 20)) }
        val copy = SharedImport.copyToInbox(File(root, "inbox"), "jpg") { original.inputStream() }!!
        original.delete()
        assertTrue(copy.exists() && copy.length() > 0)
        assertNull(SharedImport.copyToInbox(File(root, "inbox"), "pdf") { null })
        assertEquals(1, File(root, "inbox").list()!!.size)
    }

    @Test
    fun titles_forSharedPictures() {
        assertEquals("Whiteboard", SharedImport.titleForPictures(listOf("Whiteboard.jpg"), "16 Nov"))
        assertEquals("Shared pictures 16 Nov", SharedImport.titleForPictures(listOf("a.jpg", "b.jpg"), "16 Nov"))
        assertEquals("Shared pictures 16 Nov", SharedImport.titleForPictures(listOf(null), "16 Nov"))
    }
}
