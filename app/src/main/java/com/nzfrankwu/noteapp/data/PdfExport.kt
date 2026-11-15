package com.nzfrankwu.noteapp.data

import android.graphics.BitmapFactory
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.domain.PagePattern
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.ink.InkImage
import com.nzfrankwu.noteapp.domain.ink.InkStroke
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
 *  - any other page is a new page of its size with its paper pattern, pictures, then ink.
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
                    // Our page units run down from the top-left corner; PDF's run up from the bottom-left.
                    cs.transform(Matrix(1f, 0f, 0f, -1f, box.lowerLeftX, box.lowerLeftY + box.height))
                    if (original == null) drawPaper(cs, page.template ?: notebook.template, size.width, size.height)
                    for (image in content.images) drawImage(doc, cs, notebook.id, image)
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
        /** A safe file name for the exported PDF: the notebook's title, without odd characters. */
        fun fileNameFor(title: String): String {
            val clean = title.map { if (it.isLetterOrDigit() || it in " -_.()") it else '_' }.joinToString("").trim().trimEnd('.')
            return (clean.ifEmpty { "Notebook" }.take(80)) + ".pdf"
        }
    }
}
