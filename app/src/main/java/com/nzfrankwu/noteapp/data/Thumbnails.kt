package com.nzfrankwu.noteapp.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.nzfrankwu.noteapp.domain.PagePattern
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import java.io.File
import java.io.FileOutputStream

/**
 * First-page pictures for notebook tiles (PHASE-2.md 2.11). They're only a cache: kept in the
 * app's cache folder (Android may clear it; they're simply drawn again), never backed up.
 *
 * Drawn with plain Android graphics (a simple line per stroke), which is plenty at tile size.
 */
class Thumbnails(private val dir: File) {
    fun file(notebookId: String): File = File(dir, "$notebookId.png")

    /**
     * Draws the page and saves it, replacing the old picture only once the new one is written.
     * A page of an imported PDF ([pdfFile], page [pdfPage]) shows that page instead of a pattern.
     */
    fun render(
        notebookId: String, template: PageTemplate, pageWidth: Float, pageHeight: Float, strokes: List<InkStroke>,
        pdfFile: File? = null, pdfPage: Int = 0,
        /** The page's pictures, with their files. */
        images: List<Pair<com.nzfrankwu.noteapp.domain.ink.InkImage, File>> = emptyList(),
    ) {
        val scale = WIDTH_PX / pageWidth
        val bitmap = Bitmap.createBitmap(WIDTH_PX.toInt(), (pageHeight * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFFFFF.toInt())
        val pdf = pdfFile?.let { PdfPages.renderOnce(it, pdfPage, bitmap.width, bitmap.height) }
        if (pdf != null) {
            canvas.drawBitmap(pdf, 0f, 0f, null)
            pdf.recycle()
        } else if (pdfFile == null) {
            drawPattern(canvas, template, pageWidth, pageHeight, scale)
        }
        InkSketch.drawImages(canvas, images.mapNotNull { (img, file) -> ImageImport.loadForScreen(file, 512)?.let { img to it } }, scale, recycle = true)
        InkSketch.draw(canvas, strokes, scale)
        dir.mkdirs()
        val temp = File(dir, "$notebookId.png.tmp")
        FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        if (!temp.renameTo(file(notebookId))) temp.delete()
    }

    private fun drawPattern(canvas: Canvas, template: PageTemplate, w: Float, h: Float, scale: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9D1DC.toInt(); strokeWidth = 1f }
        when (template) {
            PageTemplate.BLANK -> Unit
            PageTemplate.LINED -> for (y in PagePattern.linedRows(h)) {
                canvas.drawLine(PagePattern.LINED_SIDE_MARGIN * scale, y * scale, (w - PagePattern.LINED_SIDE_MARGIN) * scale, y * scale, paint)
            }
            PageTemplate.GRID -> {
                for (x in PagePattern.gridStops(w)) canvas.drawLine(x * scale, 0f, x * scale, h * scale, paint)
                for (y in PagePattern.gridStops(h)) canvas.drawLine(0f, y * scale, w * scale, y * scale, paint)
            }
            PageTemplate.DOTS -> {
                paint.strokeWidth = 1.5f
                for (y in PagePattern.gridStops(h)) for (x in PagePattern.gridStops(w)) canvas.drawPoint(x * scale, y * scale, paint)
            }
        }
    }

    companion object {
        /** Thumbnail width in pixels (tiles are about 120 dp wide). */
        const val WIDTH_PX = 360f
    }
}

/**
 * A quick sketch of strokes (and pictures) with plain Android graphics: one line per stroke, highlighter under
 * pen. Plenty for small pictures (notebook tiles, page previews); not for the page itself.
 */
object InkSketch {
    /** Draws pictures (page units) onto [canvas] at [scale], turned as on the page. */
    fun drawImages(
        canvas: Canvas,
        images: List<Pair<com.nzfrankwu.noteapp.domain.ink.InkImage, android.graphics.Bitmap>>,
        scale: Float,
        recycle: Boolean = false,
    ) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        for ((img, bitmap) in images) {
            canvas.save()
            canvas.rotate(Math.toDegrees(img.rotation.toDouble()).toFloat(), img.centreX * scale, img.centreY * scale)
            canvas.drawBitmap(
                bitmap, null,
                android.graphics.RectF(img.left * scale, img.top * scale, (img.left + img.width) * scale, (img.top + img.height) * scale),
                paint,
            )
            canvas.restore()
            if (recycle) bitmap.recycle()
        }
    }

    /**
     * Draws [strokes] (page units) onto [canvas] at [scale] pixels per page unit. [widthFactor]
     * thins the lines (a pressure pen's line is usually thinner than its full size).
     */
    fun draw(canvas: Canvas, strokes: List<InkStroke>, scale: Float, widthFactor: Float = 1f, minWidth: Float = 1f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        for (pass in 0..1) {
            for (s in strokes) {
                val highlighter = s.tool.isHighlighter
                if (highlighter != (pass == 0) || s.pointCount == 0) continue
                paint.color = if (highlighter && (s.colorArgb ushr 24) == 0xFF) (s.colorArgb and 0x00FFFFFF) or 0x66000000 else s.colorArgb
                paint.strokeWidth = (s.size * scale * (if (highlighter) 1f else widthFactor)).coerceAtLeast(minWidth)
                path.rewind()
                path.moveTo(s.xs[0] * scale, s.ys[0] * scale)
                for (i in 1 until s.pointCount) path.lineTo(s.xs[i] * scale, s.ys[i] * scale)
                if (s.pointCount == 1) path.lineTo(s.xs[0] * scale + 0.1f, s.ys[0] * scale)
                canvas.drawPath(path, paint)
            }
        }
    }
}
