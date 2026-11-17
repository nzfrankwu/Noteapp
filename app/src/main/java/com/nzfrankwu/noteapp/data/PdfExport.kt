package com.nzfrankwu.noteapp.data

import android.graphics.BitmapFactory
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.PagePattern
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.ink.InkImage
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkText
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** A stroke's filled shape for export: outlines in page units (x, y, x, y, …) and its colour. */
class StrokeShape(val outlines: List<FloatArray>, val colorArgb: Int)

/**
 * Exports a notebook as a PDF (PHASE-3.md 3.7), with PdfBox-Android:
 *  - a page of an imported PDF is the ORIGINAL page (its text stays sharp and selectable), with
 *    the pictures and ink drawn on top;
 *  - any other page is a new page of its size with its paper pattern, pictures, then ink;
 *  - text boxes (Phase 5) are real text in the embedded Liberation Sans, laid out line by line
 *    exactly as on screen ([TextBoxLayout.lines]), so they can be selected and searched. A box with
 *    a character the font doesn't have (an emoji, Chinese) is drawn as a picture instead.
 * Ink is drawn as filled vector shapes (the same outlines the screen draws), highlighter
 * see-through. Reads the saved page files: save the notebook first.
 */
class PdfExport(
    private val files: NotebookFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Writes [pages] of [notebook] to [out]. [shapeOf] gives each stroke's outline (from the ink
     * library); [onProgress] gets (pages done, pages in all). Throws if the PDF can't be written.
     */
    suspend fun export(
        notebook: Notebook,
        pages: List<Page>,
        out: File,
        shapeOf: (InkStroke) -> StrokeShape?,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) = withContext(io) {
        val sources = HashMap<String, PDDocument?>()
        val doc = PDDocument()
        // The text boxes' font, embedded once (only the letters used) and only if a page has text.
        val font by lazy { TextBoxLayout.fontBytes()?.let { runCatching { PDType0Font.load(doc, it.inputStream(), true) }.getOrNull() } }
        try {
            pages.forEachIndexed { index, page ->
                ensureActive()
                val content = when (val read = files.read(notebook.id, page.id)) {
                    is PageRead.Ok -> read
                    PageRead.Missing -> PageRead.Ok(emptyList())
                    is PageRead.Damaged -> PageRead.Ok(emptyList()) // its file is left untouched; export what's readable
                }
                val size = page.size(notebook)
                val source = page.pdfFile?.let { name ->
                    sources.getOrPut(name) { runCatching { PDDocument.load(files.assetFile(notebook.id, name)) }.getOrNull() }
                }
                val original = source?.let { src -> page.pdfPage?.takeIf { it in 0 until src.numberOfPages }?.let { src.getPage(it) } }
                val pdfPage: PDPage
                val box: PDRectangle
                if (original != null) {
                    pdfPage = doc.importPage(original)
                    box = original.cropBox ?: original.mediaBox
                } else {
                    box = PDRectangle(size.width, size.height)
                    pdfPage = PDPage(box)
                    doc.addPage(pdfPage)
                }
                PDPageContentStream(doc, pdfPage, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    // Our page units run down from the top-left corner of the page AS SHOWN (turned
                    // by its /Rotate, as PdfRenderer shows it); PDF's run up from the bottom-left of
                    // the unturned page.
                    cs.transform(pageToPdf(box, original?.rotation ?: 0))
                    if (original == null) drawPaper(cs, page.template ?: notebook.template, size.width, size.height)
                    for (image in content.images) drawImage(doc, cs, notebook.id, image)
                    for (box in content.texts) drawText(doc, cs, box) { font }
                    drawInk(doc, cs, content.strokes, shapeOf)
                }
                onProgress(index + 1, pages.size)
            }
            out.parentFile?.mkdirs()
            val temp = File(out.parentFile, out.name + ".writing")
            doc.save(temp)
            if (!temp.renameTo(out)) {
                out.delete()
                if (!temp.renameTo(out)) throw java.io.IOException("Couldn't save the PDF")
            }
        } finally {
            runCatching { doc.close() }
            sources.values.forEach { runCatching { it?.close() } }
        }
    }

    /** The matrix from our top-down units on the page as shown to the PDF's own units (see [PdfCoords]). */
    private fun pageToPdf(box: PDRectangle, rotation: Int): Matrix {
        val m = com.nzfrankwu.noteapp.domain.PdfCoords.pageToPdf(box.lowerLeftX, box.lowerLeftY, box.width, box.height, rotation)
        return Matrix(m[0], m[1], m[2], m[3], m[4], m[5])
    }

    private fun drawPaper(cs: PDPageContentStream, template: PageTemplate, w: Float, h: Float) {
        if (template == PageTemplate.BLANK) return
        cs.saveGraphicsState()
        cs.setStrokingColor(0xC9 / 255f, 0xD1 / 255f, 0xDC / 255f)
        cs.setNonStrokingColor(0xC9 / 255f, 0xD1 / 255f, 0xDC / 255f)
        cs.setLineWidth(0.5f)
        when (template) {
            PageTemplate.LINED -> for (y in PagePattern.linedRows(h)) {
                cs.moveTo(PagePattern.LINED_SIDE_MARGIN, y)
                cs.lineTo(w - PagePattern.LINED_SIDE_MARGIN, y)
            }
            PageTemplate.GRID -> {
                for (x in PagePattern.gridStops(w)) { cs.moveTo(x, 0f); cs.lineTo(x, h) }
                for (y in PagePattern.gridStops(h)) { cs.moveTo(0f, y); cs.lineTo(w, y) }
            }
            PageTemplate.DOTS -> for (y in PagePattern.gridStops(h)) for (x in PagePattern.gridStops(w)) cs.addRect(x - 0.5f, y - 0.5f, 1f, 1f)
            PageTemplate.BLANK -> Unit
        }
        if (template == PageTemplate.DOTS) cs.fill() else cs.stroke()
        cs.restoreGraphicsState()
    }

    private fun drawImage(doc: PDDocument, cs: PDPageContentStream, notebookId: String, image: InkImage) {
        val file = runCatching { files.assetFile(notebookId, image.asset) }.getOrNull() ?: return
        if (!file.exists()) return
        val xObject: PDImageXObject = try {
            if (image.asset.endsWith(".jpg")) {
                file.inputStream().use { JPEGFactory.createFromStream(doc, it) }
            } else {
                val bitmap = BitmapFactory.decodeFile(file.path) ?: return
                LosslessFactory.createFromImage(doc, bitmap).also { bitmap.recycle() }
            }
        } catch (e: Exception) {
            return // a picture that can't be read is left out, the rest of the page still exports
        }
        cs.saveGraphicsState()
        if (image.rotation != 0f) {
            cs.transform(Matrix.getRotateInstance(image.rotation.toDouble(), image.centreX, image.centreY))
            cs.transform(Matrix.getTranslateInstance(-image.centreX, -image.centreY))
        }
        // The picture's unit square, flipped back upright inside our top-down page units.
        cs.drawImage(xObject, Matrix(image.width, 0f, 0f, -image.height, image.left, image.top + image.height))
        cs.restoreGraphicsState()
    }

    /**
     * Draws a text box as text, line by line where the screen puts each line. Our units run down
     * the page, so each line's text matrix flips it upright again. Bold is drawn filled and
     * outlined, like Android's thicker "fake bold".
     */
    private fun drawText(doc: PDDocument, cs: PDPageContentStream, box: InkText, font: () -> PDType0Font?) {
        val lines = TextBoxLayout.lines(box)
        if (lines.isEmpty()) return
        val f = font()
        // Every character must be in the font, or the box becomes a picture.
        val encodable = f != null && lines.all { line -> runCatching { f.encode(line.text) }.isSuccess }
        if (!encodable) {
            drawTextAsPicture(doc, cs, box)
            return
        }
        val argb = box.colorArgb
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        cs.saveGraphicsState()
        cs.setNonStrokingColor(r, g, b)
        if (box.bold) {
            cs.setStrokingColor(r, g, b)
            cs.setLineWidth(box.size * BOLD_STROKE)
        }
        cs.beginText()
        cs.setFont(f, 1f)
        if (box.bold) cs.setRenderingMode(RenderingMode.FILL_STROKE)
        for (line in lines) {
            cs.setTextMatrix(Matrix(box.size, 0f, 0f, -box.size, line.x, line.baseline))
            cs.showText(line.text)
        }
        cs.endText()
        cs.restoreGraphicsState()
    }

    /** A text box drawn by Android (with its fallback fonts) and placed as a picture. */
    private fun drawTextAsPicture(doc: PDDocument, cs: PDPageContentStream, box: InkText) {
        val scale = TEXT_PICTURE_SCALE
        val w = (box.width * scale).toInt().coerceAtLeast(1)
        val h = (box.height * scale).toInt().coerceAtLeast(1)
        if (w.toLong() * h > MAX_TEXT_PICTURE_PIXELS) return
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(scale, scale)
            TextBoxLayout.draw(canvas, box.copy(left = 0f, top = 0f))
            val xObject = LosslessFactory.createFromImage(doc, bitmap)
            cs.drawImage(xObject, Matrix(box.width, 0f, 0f, -box.height, box.left, box.top + box.height))
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawInk(doc: PDDocument, cs: PDPageContentStream, strokes: List<InkStroke>, shapeOf: (InkStroke) -> StrokeShape?) {
        // Highlighter under pen, as on screen.
        val ordered = strokes.filter { it.tool.isHighlighter } + strokes.filter { !it.tool.isHighlighter }
        val alphaStates = HashMap<Int, PDExtendedGraphicsState>()
        for (stroke in ordered) {
            val shape = shapeOf(stroke) ?: continue
            if (shape.outlines.isEmpty()) continue
            val argb = shape.colorArgb
            val alpha = (argb ushr 24) and 0xFF
            cs.saveGraphicsState()
            if (alpha < 0xFF) {
                cs.setGraphicsStateParameters(
                    alphaStates.getOrPut(alpha) { PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = alpha / 255f } },
                )
            }
            cs.setNonStrokingColor(((argb shr 16) and 0xFF) / 255f, ((argb shr 8) and 0xFF) / 255f, (argb and 0xFF) / 255f)
            for (outline in shape.outlines) {
                if (outline.size < 6) continue
                cs.moveTo(outline[0], outline[1])
                var i = 2
                while (i + 1 < outline.size) {
                    cs.lineTo(outline[i], outline[i + 1])
                    i += 2
                }
                cs.closePath()
            }
            cs.fill() // non-zero winding, like the screen
            cs.restoreGraphicsState()
        }
    }

    companion object {
        /** How thick bold text's outline is, for its font size (close to Android's fake bold). */
        private const val BOLD_STROKE = 0.035f

        /** Pixels per page unit for a text box drawn as a picture (about 290 dpi). */
        private const val TEXT_PICTURE_SCALE = 4f
        private const val MAX_TEXT_PICTURE_PIXELS = 16_000_000L

        /** A safe file name for the exported PDF: the notebook's title, without odd characters. */
        fun fileNameFor(title: String): String {
            val clean = title.map { if (it.isLetterOrDigit() || it in " -_.()") it else '_' }.joinToString("").trim().trimEnd('.')
            return (clean.ifEmpty { "Notebook" }.take(80)) + ".pdf"
        }
    }
}
