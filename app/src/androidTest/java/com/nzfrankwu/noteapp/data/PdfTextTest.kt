package com.nzfrankwu.noteapp.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.PageSize
import com.nzfrankwu.noteapp.domain.ink.PdfWord
import com.nzfrankwu.noteapp.domain.ink.TextRange
import com.nzfrankwu.noteapp.domain.ink.TextSelection
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

/**
 * Reading a PDF page's words and where they are (PHASE-4.md 4.6), checked against what Android's
 * PdfRenderer draws: every word's box must cover the word's ink, on plain and turned pages.
 */
@RunWith(AndroidJUnit4::class)
class PdfTextTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        root = File(context.cacheDir, "pdf-text-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    /** A landscape page (842 × 595) with two lines of text, made with Android's PdfDocument. */
    private fun twoLinePdf(blankSecondPage: Boolean = false): ByteArray {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 28f; color = Color.BLACK }
        val page = doc.startPage(PdfDocument.PageInfo.Builder(842, 595, 1).create())
        page.canvas.drawText("Transmission lines", 60f, 80f, paint)
        page.canvas.drawText("Coaxial cable", 60f, 140f, paint)
        doc.finishPage(page)
        if (blankSecondPage) doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(842, 595, 2).create()))
        return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }

    /** [bytes] saved with every page turned by [rotation] degrees (/Rotate). */
    private fun turned(bytes: ByteArray, rotation: Int): ByteArray =
        PDDocument.load(bytes).use { doc ->
            doc.pages.forEach { it.rotation = rotation }
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }

    private fun file(bytes: ByteArray, name: String = "test.pdf") = File(root, name).apply { writeBytes(bytes) }

    /** Page 0 as PdfRenderer shows it, 1 pixel per page unit. */
    private fun render(pdf: File): Bitmap =
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(0).use { p ->
                    Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    private fun darkPixels(bitmap: Bitmap, l: Int, t: Int, r: Int, b: Int): Int {
        var n = 0
        for (y in t.coerceAtLeast(0) until b.coerceAtMost(bitmap.height)) {
            for (x in l.coerceAtLeast(0) until r.coerceAtMost(bitmap.width)) {
                if (Color.red(bitmap.getPixel(x, y)) < 128) n++
            }
        }
        return n
    }

    /** Every dark pixel of the page lies inside some word's box (give or take a pixel), and every box has ink. */
    private fun assertBoxesCoverTheInk(words: List<PdfWord>, bitmap: Bitmap) {
        for (w in words) {
            assertTrue("No ink in the box of ${w.text}", darkPixels(bitmap, w.left.toInt(), w.top.toInt(), w.right.toInt() + 1, w.bottom.toInt() + 1) > 20)
        }
        var outside = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.red(bitmap.getPixel(x, y)) < 128 &&
                words.none { x >= it.left - 2 && x <= it.right + 2 && y >= it.top - 2 && y <= it.bottom + 2 }
            ) outside++
        }
        assertEquals("Ink outside every word's box", 0, outside)
    }

    @Test
    fun plainPage_wordsAndLines_matchWhatIsDrawn() {
        val pdf = file(twoLinePdf())
        val text = PDDocument.load(pdf).use { PdfText.read(it, 0, PageSize(842f, 595f)) }
        assertEquals(listOf("Transmission", "lines", "Coaxial", "cable"), text.words.map { it.text })
        assertEquals(listOf(0, 0, 1, 1), text.words.map { it.line })
        val first = text.words[0]
        assertEquals(60f, first.left, 3f)
        assertEquals(80f, first.baseline, 2f)
        assertTrue(first.top < 62f && first.bottom > 82f)
        assertEquals("Transmission lines\nCoaxial", TextSelection.copyText(text, TextRange(0, 2)))
        assertBoxesCoverTheInk(text.words, render(pdf))
    }

    @Test
    fun turnedPages_lineUpWithWhatPdfRendererShows() {
        for (rotation in listOf(90, 180, 270)) {
            val pdf = file(turned(twoLinePdf(), rotation), "turned-$rotation.pdf")
            val size = if (rotation == 180) PageSize(842f, 595f) else PageSize(595f, 842f)
            val text = PDDocument.load(pdf).use { PdfText.read(it, 0, size) }
            assertEquals("Turned $rotation", listOf("Transmission", "lines", "Coaxial", "cable"), text.words.map { it.text })
            val bitmap = render(pdf)
            assertEquals(size.width.toInt(), bitmap.width)
            assertBoxesCoverTheInk(text.words, bitmap)
        }
    }

    @Test
    fun aPageWithoutText_isEmpty_andTheReaderKeepsPagesItRead() = runBlocking {
        val files = NotebookFiles(root)
        val pdfName = "pdf-1.pdf"
        files.assetFile("nb", pdfName).apply { parentFile?.mkdirs(); writeBytes(twoLinePdf(blankSecondPage = true)) }
        val now = Instant.now()
        fun page(id: String, index: Int) = Page(id, "nb", index, null, now, now, null, 842f, 595f, pdfName, index)
        PdfText(files, "nb").use { reader ->
            val blank = reader.pageText(page("p2", 1), PageSize(842f, 595f))
            assertTrue(blank.isEmpty)
            val first = reader.pageText(page("p1", 0), PageSize(842f, 595f))
            assertEquals(4, first.words.size)
            assertTrue(reader.cached(page("p1", 0)) === first)
            // A plain page (no PDF behind it) has no text.
            assertTrue(reader.pageText(Page("p3", "nb", 2, null, now, now), PageSize(595f, 842f)).isEmpty)
        }
    }
}
