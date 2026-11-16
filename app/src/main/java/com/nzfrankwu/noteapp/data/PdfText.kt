package com.nzfrankwu.noteapp.data

import android.util.LruCache
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.PageSize
import com.nzfrankwu.noteapp.domain.PdfCoords
import com.nzfrankwu.noteapp.domain.ink.PageText
import com.nzfrankwu.noteapp.domain.ink.PdfWord
import com.nzfrankwu.noteapp.domain.ink.TextSelection
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.Writer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The words of an imported PDF's pages, with where they are (PHASE-4.md 4.6), read with
 * PdfBox-Android the first time a page is held and then kept for the open notebook.
 *
 * Positions are turned into our page units (top-left of the page as shown, 1/72 inch) through
 * [PdfCoords], the same mapping the PDF export uses, so turned pages (/Rotate) and crop boxes line
 * up with what's drawn. Pages with no text (scans) give [PageText.EMPTY]. One per open notebook;
 * [close] when it closes.
 */
class PdfText(
    private val files: NotebookFiles,
    private val notebookId: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    private val lock = Mutex()
    private val documents = HashMap<String, PDDocument?>()
    private val cache = LruCache<String, PageText>(MAX_PAGES)
    @Volatile private var closed = false

    /** The text already read for [page], or null if it hasn't been read yet. */
    fun cached(page: Page): PageText? = cache.get(page.id)

    /** The words of [page] (a page of an imported PDF, shown at [size]); empty for any other page. */
    suspend fun pageText(page: Page, size: PageSize): PageText {
        cache.get(page.id)?.let { return it }
        val file = page.pdfFile ?: return PageText.EMPTY
        val index = page.pdfPage ?: return PageText.EMPTY
        return lock.withLock {
            if (closed) return@withLock PageText.EMPTY
            cache.get(page.id) ?: withContext(io) {
                val doc = documents.getOrPut(file) {
                    runCatching { PDDocument.load(files.assetFile(notebookId, file)) }.getOrNull()
                }
                val text = if (doc == null || index !in 0 until doc.numberOfPages) {
                    PageText.EMPTY
                } else {
                    runCatching { read(doc, index, size) }.getOrDefault(PageText.EMPTY)
                }
                text.also { cache.put(page.id, it) }
            }
        }
    }

    /**
     * Closes the PDFs. Waits for a read in progress (they share the documents), so call it from
     * a background coroutine; afterwards every page reads as empty.
     */
    suspend fun closeAll() = lock.withLock {
        closed = true
        withContext(io) {
            documents.values.forEach { runCatching { it?.close() } }
            documents.clear()
        }
        cache.evictAll()
    }

    /** Blocking [closeAll], for tests and `use { }`. */
    override fun close() = kotlinx.coroutines.runBlocking { closeAll() }

    companion object {
        /** Pages whose text is kept at once. */
        const val MAX_PAGES = 40

        /** How far above the baseline a word's box reaches, and below it, as parts of the font size. */
        private const val ASCENT = 0.86f
        private const val DESCENT = 0.24f

        /** Reads page [index] (0 = first) of [doc], shown at [size] in our units. */
        fun read(doc: PDDocument, index: Int, size: PageSize): PageText {
            val pdfPage = doc.getPage(index)
            val box = pdfPage.cropBox ?: pdfPage.mediaBox
            val rotation = pdfPage.rotation
            val toPage = PdfCoords.pdfToPage(box.lowerLeftX, box.lowerLeftY, box.width, box.height, rotation)
            // Our units for the page as shown; scaled in case the saved size was rounded.
            val turned = ((rotation % 180) + 180) % 180 == 90
            val shownW = if (turned) box.height else box.width
            val shownH = if (turned) box.width else box.height
            val sx = if (shownW > 0f) size.width / shownW else 1f
            val sy = if (shownH > 0f) size.height / shownH else 1f
            val stripper = WordStripper(toPage, box.lowerLeftX, box.lowerLeftY, sx, sy)
            stripper.sortByPosition = true
            stripper.startPage = index + 1
            stripper.endPage = index + 1
            stripper.writeText(doc, NullWriter)
            return TextSelection.build(stripper.words)
        }
    }

    /** Collects each word PdfBox finds, with its box in our page units. */
    private class WordStripper(
        private val toPage: FloatArray,
        private val cropX: Float,
        private val cropY: Float,
        private val sx: Float,
        private val sy: Float,
    ) : PDFTextStripper() {
        val words = ArrayList<Pair<PdfWord, Boolean>>()
        private var newLine = true

        /**
         * PdfBox hands over a run of text (often a whole line); it's split into words here, at
         * spaces and at gaps wider than a fifth of the font size (spacing done by moving the pen).
         */
        override fun writeString(text: String, textPositions: List<TextPosition>) {
            var word: Glyphs? = null
            for (tp in textPositions) {
                val glyph = glyphOf(tp)
                val blank = tp.unicode.isNullOrBlank()
                val gap = word != null && glyph.gapAfter(word.last) > glyph.fontSize * 0.2f
                if (word != null && (blank || gap)) {
                    finish(word)
                    word = null
                }
                if (blank) continue
                word = word?.also { it.add(glyph) } ?: Glyphs(glyph)
            }
            word?.let { finish(it) }
        }

        private fun finish(word: Glyphs) {
            val g = word.items
            words += PdfWord(
                text = g.joinToString("") { it.text },
                left = g.minOf { it.left }, top = g.minOf { it.top }, right = g.maxOf { it.right }, bottom = g.maxOf { it.bottom },
                baseline = g.map { it.baseline }.average().toFloat(),
                line = 0,
            ) to newLine
            newLine = false
        }

        /** One character's box in our units, and where the next one would start. */
        private fun glyphOf(tp: TextPosition): Glyph {
            val m = tp.textMatrix
            // PdfBox gives positions relative to the crop box's corner; put that back.
            val px = m.translateX + cropX
            val py = m.translateY + cropY
            val a = m.getValue(0, 0)
            val b = m.getValue(0, 1)
            val c = m.getValue(1, 0)
            val d = m.getValue(1, 1)
            val along = hypot(a, b).takeIf { it > 0f } ?: 1f
            val upLen = hypot(c, d)
            val fontSize = if (upLen > 0f) upLen else max(tp.fontSizeInPt, 1f)
            val ux = a / along
            val uy = b / along
            val nx = if (upLen > 0f) c / upLen else -uy
            val ny = if (upLen > 0f) d / upLen else ux
            val w = tp.widthDirAdj.takeIf { it > 0f } ?: (fontSize * 0.5f)
            val up = fontSize * ASCENT
            val down = fontSize * DESCENT
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for ((dx, dy) in listOf(
                -nx * down to -ny * down,
                ux * w - nx * down to uy * w - ny * down,
                nx * up to ny * up,
                ux * w + nx * up to uy * w + ny * up,
            )) {
                val (x, y) = PdfCoords.apply(toPage, px + dx, py + dy)
                left = min(left, x * sx)
                right = max(right, x * sx)
                top = min(top, y * sy)
                bottom = max(bottom, y * sy)
            }
            val (sxp, syp) = PdfCoords.apply(toPage, px, py)
            val (exp, eyp) = PdfCoords.apply(toPage, px + ux * w, py + uy * w)
            return Glyph(tp.unicode, left, top, right, bottom, syp * sy, sxp * sx, syp * sy, exp * sx, eyp * sy, fontSize)
        }

        override fun writeLineSeparator() {
            newLine = true
        }

        override fun writePageStart() {
            newLine = true
        }
    }

    /** One character: its box, baseline, where it starts and ends along its line, and its size. */
    private class Glyph(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val baseline: Float,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val fontSize: Float,
    ) {
        /** How far this character starts after [previous] ends. */
        fun gapAfter(previous: Glyph): Float = hypot(startX - previous.endX, startY - previous.endY)
    }

    private class Glyphs(first: Glyph) {
        val items = arrayListOf(first)
        val last: Glyph get() = items.last()
        fun add(g: Glyph) { items += g }
    }

    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }
}
