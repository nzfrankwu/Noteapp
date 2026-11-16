package com.nzfrankwu.noteapp.data

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import com.nzfrankwu.noteapp.domain.ink.InkText
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * How a text box's text is laid out and drawn (Phase 5, step 5.2). Everything that shows a text
 * box uses this: the page on screen, the page previews, notebook tiles, and the PDF export
 * (through [lines]), so the lines always break in the same places.
 *
 * The font is Liberation Sans (SIL Open Font Licence), which already comes with the app inside
 * PdfBox-Android, so it's the same on every tablet and can be embedded in exported PDFs. Bold is
 * drawn thicker ("fake bold"), which doesn't move any letters, so a bold box breaks its lines
 * exactly like a normal one. Characters the font lacks (e.g. emoji) are drawn on screen with
 * Android's own fallback fonts.
 *
 * Layout runs in page units scaled by [SCALE], because Android lays text out to whole pixels.
 */
object TextBoxLayout {
    /** PdfBox-Android's copy of Liberation Sans, inside the app's assets. */
    const val FONT_ASSET = "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"

    /** Page units are multiplied by this for Android's text layout, which works in whole pixels. */
    private const val SCALE = 8f

    @Volatile private var assets: AssetManager? = null

    @Volatile var typeface: Typeface = Typeface.SANS_SERIF
        private set

    /** Loads the font (once, at app start). Without it, text uses Android's sans-serif. */
    fun install(assets: AssetManager) {
        if (this.assets != null) return
        this.assets = assets
        typeface = runCatching { Typeface.createFromAsset(assets, FONT_ASSET) }.getOrDefault(Typeface.SANS_SERIF)
        cache.evictAll()
    }

    /** The font file's bytes, for embedding in a PDF (null if it couldn't be read). */
    fun fontBytes(): ByteArray? = assets?.let { a -> runCatching { a.open(FONT_ASSET).use { it.readBytes() } }.getOrNull() }

    private val cache = LruCache<InkText, StaticLayout>(256)

    /** The laid-out text of [box] (in page units × [SCALE]). Cached: boxes are drawn every frame. */
    private fun layout(box: InkText): StaticLayout {
        // Only what changes the layout matters, not where the box is.
        val key = box.copy(id = "", left = 0f, top = 0f, height = 0f)
        cache.get(key)?.let { return it }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface = this@TextBoxLayout.typeface
            textSize = box.size * SCALE
            color = box.colorArgb
            isFakeBoldText = box.bold
        }
        val width = (box.width * SCALE).roundToInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(box.text, 0, box.text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, LINE_SPACING)
            .build()
        cache.put(key, layout)
        return layout
    }

    /**
     * How tall [box]'s text is (page units) at its width: at least one line, so an empty box
     * being typed into still has a height.
     */
    fun height(box: InkText): Float {
        val l = layout(box.copy(text = box.text.ifEmpty { " " }))
        return ceil(l.height / SCALE * 100f) / 100f
    }

    /** [box] with its [InkText.height] set to what its text takes now. */
    fun measured(box: InkText): InkText = box.copy(height = height(box))

    /** Draws [box] onto [canvas], which is in page units (any further scaling is the caller's). */
    fun draw(canvas: Canvas, box: InkText) {
        if (box.text.isEmpty()) return
        val l = layout(box)
        canvas.save()
        canvas.translate(box.left, box.top)
        canvas.scale(1f / SCALE, 1f / SCALE)
        l.draw(canvas)
        canvas.restore()
    }

    /** Draws every box of a page onto [canvas] at [scale] pixels per page unit. */
    fun drawAll(canvas: Canvas, boxes: List<InkText>, scale: Float) {
        if (boxes.isEmpty()) return
        canvas.save()
        canvas.scale(scale, scale)
        for (box in boxes) draw(canvas, box)
        canvas.restore()
    }

    /** One laid-out line of a box, in page units: its text, where it starts, and its baseline. */
    data class Line(val text: String, val x: Float, val baseline: Float, val width: Float)

    /** The box's lines as laid out, in page units relative to the page (for the PDF export). */
    fun lines(box: InkText): List<Line> {
        if (box.text.isEmpty()) return emptyList()
        val l = layout(box)
        return (0 until l.lineCount).mapNotNull { i ->
            val text = box.text.substring(l.getLineStart(i), l.getLineEnd(i)).trimEnd('\n', '\r', ' ')
            if (text.isEmpty()) {
                null
            } else {
                Line(
                    text = text,
                    x = box.left + l.getLineLeft(i) / SCALE,
                    baseline = box.top + l.getLineBaseline(i) / SCALE,
                    width = l.getLineWidth(i) / SCALE,
                )
            }
        }
    }

    /** A little more than the font's own line height, for easier reading of handwritten-page notes. */
    private const val LINE_SPACING = 1.15f
}
