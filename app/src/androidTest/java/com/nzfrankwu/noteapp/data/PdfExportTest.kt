package com.nzfrankwu.noteapp.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.PageSize
import com.nzfrankwu.noteapp.domain.ink.InkImage
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import com.nzfrankwu.noteapp.ui.notebook.InkBrushes
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Export as PDF (PHASE-3.md 3.7), checked by reading the exported file back. */
@RunWith(AndroidJUnit4::class)
class PdfExportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NoteAppDatabase
    private lateinit var root: File
    private lateinit var files: NotebookFiles
    private lateinit var notebooks: NotebookRepository
    private lateinit var folderId: String

    @Before
    fun setUp() = runBlocking {
        PDFBoxResourceLoader.init(context)
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        root = File(context.cacheDir, "pdf-export-test").apply { deleteRecursively(); mkdirs() }
        files = NotebookFiles(root)
        notebooks = NotebookRepository(db)
        folderId = (FolderRepository(db).createFolder("ELECTENG 204", null) as FolderResult.Success).folderId
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    /** A one-page landscape PDF with a line of text (made with Android's PdfDocument). */
    private fun lecturePdf(): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
        page.canvas.drawText("Transmission lines", 60f, 80f, Paint().apply { textSize = 28f; color = Color.BLACK })
        doc.finishPage(page)
        return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }

    /** A thick horizontal pen line from (x0, y) to (x1, y), in page units. */
    private fun line(id: String, x0: Float, x1: Float, y: Float, tool: InkTool = InkTool.PEN, color: Int = Color.BLACK): InkStroke {
        val n = 20
        return InkStroke(
            id, tool, color, 8f,
            FloatArray(n) { x0 + (x1 - x0) * it / (n - 1) }, FloatArray(n) { y }, IntArray(n) { it * 10 },
            FloatArray(n) { 0.8f }, FloatArray(n) { InkStroke.NONE }, FloatArray(n) { InkStroke.NONE }, InkStroke.TOOL_STYLUS,
        )
    }

    private fun render(pdf: File, page: Int, scale: Float = 1f): Bitmap =
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(page).use { p ->
                    Bitmap.createBitmap((p.width * scale).toInt(), (p.height * scale).toInt(), Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    private fun isDark(c: Int) = Color.red(c) < 90 && Color.green(c) < 90 && Color.blue(c) < 90

    @Test
    fun aPdfNotebook_exportsWithTheOriginalPages_inkOnTop_andAWrittenBlankPage() = runBlocking {
        val id = (PdfImport(notebooks, files).import(folderId, "Lecture 7") { lecturePdf().inputStream() } as PdfImportResult.Success).notebookId
        notebooks.addPage(id, 1) // a blank page after the slide (lined, same size)
        val pages = notebooks.getPages(id)
        files.write(id, pages[0].id, listOf(line("a", 100f, 700f, 300f)))
        // A red picture on the blank page, with a line over it.
        val red = Bitmap.createBitmap(100, 50, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val picture = ImageImport(files).importBitmap(id, red)!!
        files.write(id, pages[1].id, listOf(line("b", 50f, 800f, 500f)), listOf(InkImage("i", picture.asset, 300f, 100f, 200f, 100f)))

        val out = File(root, "out/Lecture 7.pdf")
        var progress = 0
        PdfExport(files).export(notebooks.getNotebook(id)!!, notebooks.getPages(id), out, InkBrushes::exportShape) { done, _ -> progress = done }
        assertEquals(2, progress)

        PDDocument.load(out).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals(842f, doc.getPage(0).mediaBox.width, 0.5f)
            assertEquals(595f, doc.getPage(1).mediaBox.height, 0.5f)
            // The slide's text is still real text (sharp and selectable), not a picture.
            val text = PDFTextStripper().apply { startPage = 1; endPage = 1 }.getText(doc)
            assertTrue("Text kept: $text", text.contains("Transmission lines"))
        }
        val slide = render(out, 0)
        assertTrue("Ink drawn on the slide", isDark(slide.getPixel(400, 300)))
        assertTrue("Above the ink stays white", !isDark(slide.getPixel(400, 200)))
        val blank = render(out, 1)
        val inPicture = blank.getPixel(400, 150)
        assertTrue("The picture shows: ${Integer.toHexString(inPicture)}", Color.red(inPicture) > 200 && Color.green(inPicture) < 60)
        assertTrue("Ink on the blank page", isDark(blank.getPixel(400, 500)))
    }

    @Test
    fun highlighter_isSeeThrough() = runBlocking {
        val id = (notebooks.createNotebook(folderId, "Plain") as NotebookResult.Success).id
        val page = notebooks.getPages(id).single()
        files.write(
            id, page.id,
            listOf(line("ink", 100f, 500f, 300f), line("hl", 80f, 520f, 300f, tool = InkTool.HIGHLIGHTER, color = Color.YELLOW)),
        )
        val out = File(root, "plain.pdf")
        PdfExport(files).export(notebooks.getNotebook(id)!!, notebooks.getPages(id), out, InkBrushes::exportShape)
        val bmp = render(out, 0)
        // Under the highlighter but beside the pen line: yellowish, not white, not opaque yellow over the line.
        val beside = bmp.getPixel(90, 300)
        assertTrue("Highlighter shows: ${Integer.toHexString(beside)}", Color.blue(beside) < 200 && Color.red(beside) > 180)
        assertTrue("The pen line shows through the highlighter", Color.red(bmp.getPixel(300, 300)) < 150)
        assertEquals(PageSize(595f, 842f), PageSize(bmp.width.toFloat(), bmp.height.toFloat()))
    }

    @Test
    fun fileNames_areSafe() {
        assertEquals("Lecture 7.pdf", PdfExport.fileNameFor("Lecture 7"))
        assertEquals("a_b_c.pdf", PdfExport.fileNameFor("a/b\\c"))
        assertEquals("Notebook.pdf", PdfExport.fileNameFor("  "))
    }
}
