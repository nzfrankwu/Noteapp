package com.nzfrankwu.noteapp.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.PageSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Importing PDFs as notebooks (PHASE-3.md 3.3), with real PDFs made on the device. */
@RunWith(AndroidJUnit4::class)
class PdfImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NoteAppDatabase
    private lateinit var root: File
    private lateinit var files: NotebookFiles
    private lateinit var notebooks: NotebookRepository
    private lateinit var folderId: String

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        root = File(context.cacheDir, "pdf-import-test").apply { deleteRecursively(); mkdirs() }
        files = NotebookFiles(root)
        notebooks = NotebookRepository(db)
        folderId = (FolderRepository(db).createFolder("ELECTENG 204", null) as FolderResult.Success).folderId
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    /** A real PDF: two landscape slides and a US Letter page, each with a black box on it. */
    private fun samplePdf(sizes: List<PageSize> = listOf(PageSize(842f, 595f), PageSize(842f, 595f), PageSize(612f, 792f))): ByteArray {
        val doc = PdfDocument()
        sizes.forEachIndexed { i, size ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(size.width.toInt(), size.height.toInt(), i + 1).create())
            page.canvas.drawRect(100f, 100f, 300f, 200f, Paint().apply { color = Color.BLACK })
            doc.finishPage(page)
        }
        return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }

    private fun notebookDirs() = File(root, NotebookFiles.DIR).listFiles()?.toList().orEmpty()

    @Test
    fun aPdf_becomesANotebook_withOnePagePerPdfPage() = runBlocking {
        val bytes = samplePdf()
        val result = PdfImport(notebooks, files).import(folderId, "Lecture 7") { ByteArrayInputStream(bytes) }
        val id = (result as PdfImportResult.Success).notebookId

        val pages = notebooks.getPages(id)
        assertEquals(3, pages.size)
        assertEquals(listOf(0, 1, 2), pages.map { it.pdfPage })
        assertEquals(listOf(842f, 842f, 612f), pages.map { it.width })
        assertEquals(listOf(595f, 595f, 792f), pages.map { it.height })
        val pdf = files.assetFile(id, pages.first().pdfFile!!)
        assertTrue("The PDF is copied into the notebook's folder", pdf.exists())
        assertTrue(pdf.name.startsWith("pdf-") && pdf.name.endsWith(".pdf"))
        assertEquals(File(File(root, NotebookFiles.DIR), id), pdf.parentFile)
        assertTrue("Copied in whole", pdf.readBytes().contentEquals(bytes))

        val tile = notebooks.observeInFolder(folderId).first().single()
        assertEquals("Lecture 7", tile.notebook.title)
        assertTrue(tile.isPdf)
        assertEquals(3, tile.pageCount)
    }

    @Test
    fun aBrokenFile_makesNothing_andLeavesNoCopy() = runBlocking {
        val result = PdfImport(notebooks, files).import(folderId, "Junk") { ByteArrayInputStream("not a pdf".toByteArray()) }
        assertEquals(PdfImportResult.Failure(PdfImport.BROKEN), result)
        assertEquals(0, notebooks.observeInFolder(folderId).first().size)
        assertEquals(emptyList<File>(), notebookDirs())
    }

    @Test
    fun aPasswordProtectedPdf_saysSo_andMakesNothing() = runBlocking {
        val importer = PdfImport(notebooks, files, readInfo = { PdfInfo.PasswordProtected })
        val result = importer.import(folderId, "Locked") { ByteArrayInputStream(samplePdf()) }
        assertEquals(PdfImportResult.Failure(PdfImport.PASSWORD), result)
        assertEquals(0, notebooks.observeInFolder(folderId).first().size)
        assertEquals(emptyList<File>(), notebookDirs())
    }

    @Test
    fun aFileThatCantBeRead_makesNothing() = runBlocking {
        assertEquals(PdfImportResult.Failure(PdfImport.CANT_READ), PdfImport(notebooks, files).import(folderId, "x") { null })
        val failing = PdfImport(notebooks, files).import(folderId, "x") {
            object : java.io.InputStream() { override fun read(): Int = throw java.io.IOException("gone") }
        }
        assertEquals(PdfImportResult.Failure(PdfImport.CANT_READ), failing)
        assertEquals(0, notebooks.observeInFolder(folderId).first().size)
        assertEquals(emptyList<File>(), notebookDirs())
    }

    @Test
    fun aMissingFolder_makesNothing() = runBlocking {
        val result = PdfImport(notebooks, files).import("no-such-folder", "x") { ByteArrayInputStream(samplePdf()) }
        assertTrue(result is PdfImportResult.Failure)
        assertEquals(emptyList<File>(), notebookDirs())
    }

    @Test
    fun readInfo_readsEachPagesSize() {
        val file = File(root, "sample.pdf").apply { writeBytes(samplePdf()) }
        val info = PdfPages.readInfo(file) as PdfInfo.Ok
        assertEquals(listOf(PageSize(842f, 595f), PageSize(842f, 595f), PageSize(612f, 792f)), info.pageSizes)
        assertEquals(PdfInfo.Broken, PdfPages.readInfo(File(root, "missing.pdf")))
    }

    @Test
    fun pages_areDrawn_wholeAndInParts() = runBlocking {
        val file = File(root, "sample.pdf").apply { writeBytes(samplePdf()) }
        PdfPages().use { pdf ->
            val whole = pdf.wholePage(file, 0, PageSize(842f, 595f), 1f)
            assertNotNull(whole)
            assertEquals(842, whole!!.bitmap.width)
            assertEquals(595, whole.bitmap.height)
            // The black box at (100, 100)–(300, 200) points.
            assertEquals(Color.BLACK, whole.bitmap.getPixel(200, 150) or 0xFF000000.toInt())
            // Away from the box, nothing dark is drawn.
            val blank = whole.bitmap.getPixel(500, 400)
            assertTrue(Color.alpha(blank) == 0 || Color.red(blank) > 200)
            // The same picture comes from the cache.
            assertTrue(whole === pdf.wholePage(file, 0, PageSize(842f, 595f), 1f))

            // A part: (100, 100)–(200, 150) points at 4 px per point → the box's corner fills it.
            val part = pdf.region(file, 0, 100f, 100f, 200f, 150f, 4f)!!
            assertEquals(400, part.bitmap.width)
            assertEquals(200, part.bitmap.height)
            assertEquals(255, Color.alpha(part.bitmap.getPixel(200, 100)))
            assertEquals(0, Color.red(part.bitmap.getPixel(200, 100)))

            assertNull("A page past the end draws nothing", pdf.wholePage(file, 9, PageSize(842f, 595f), 1f))
        }
    }

    @Test
    fun theThumbnail_showsThePdfsFirstPage() {
        val file = File(root, "sample.pdf").apply { writeBytes(samplePdf()) }
        val thumbs = Thumbnails(File(root, "thumbs"))
        thumbs.render("nb", com.nzfrankwu.noteapp.domain.PageTemplate.LINED, 842f, 595f, emptyList(), file, 0)
        val png = android.graphics.BitmapFactory.decodeFile(thumbs.file("nb").path)
        assertNotNull(png)
        val s = Thumbnails.WIDTH_PX / 842f
        val inBox = png.getPixel((200 * s).toInt(), (150 * s).toInt())
        assertTrue("The box shows dark", Color.red(inBox) < 80)
        assertFalse(png.getPixel(5, 5) == Color.BLACK)
    }
}
