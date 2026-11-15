package com.nzfrankwu.noteapp.ui.notebook

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.InputToolType
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import java.util.UUID

/**
 * The bridge between our saved strokes ([InkStroke], page units) and Jetpack Ink's drawable
 * [Stroke]s. Brush versions are pinned (V1) to match the tool keys, so updating the library
 * can't change how old notes look.
 */
object InkBrushes {
    /** Fine detail kept when building stroke shapes, in page units (about 0.02 mm). */
    private const val EPSILON = 0.05f

    private val penFamily: BrushFamily by lazy { StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1) }
    // DISCARD: where a highlighter stroke crosses itself it doesn't get darker, like a real one.
    private val highlighterFamily: BrushFamily by lazy { StockBrushes.highlighter(SelfOverlap.DISCARD, StockBrushes.HighlighterVersion.V1) }

    fun family(tool: InkTool): BrushFamily = when (tool) {
        InkTool.PEN -> penFamily
        InkTool.HIGHLIGHTER -> highlighterFamily
    }

    fun brush(tool: InkTool, colorArgb: Int, size: Float): Brush =
        Brush.createWithColorIntArgb(family(tool), if (tool == InkTool.HIGHLIGHTER) seeThrough(colorArgb) else colorArgb, size, EPSILON)

    /** Highlighter ink is see-through, so the writing and lines under it still show. */
    private fun seeThrough(argb: Int): Int =
        if ((argb ushr 24) == 0xFF) (argb and 0x00FFFFFF) or (HIGHLIGHTER_ALPHA shl 24) else argb

    private const val HIGHLIGHTER_ALPHA = 0x66

    /** Which tool drew a finished stroke (by its brush). */
    fun toolOf(stroke: Stroke): InkTool = if (stroke.brush.family == highlighterFamily) InkTool.HIGHLIGHTER else InkTool.PEN

    /**
     * Turns a just-finished Jetpack Ink stroke (desk coordinates) into a saved stroke on its page,
     * whose top-left corner is at ([pageLeft], [pageTop]) on the desk.
     */
    fun toSaved(stroke: Stroke, pageLeft: Float, pageTop: Float, id: String = UUID.randomUUID().toString()): InkStroke {
        val inputs = stroke.inputs
        val n = inputs.size
        val scratch = StrokeInput()
        val xs = FloatArray(n); val ys = FloatArray(n); val ts = IntArray(n)
        val pressure = FloatArray(n); val tilt = FloatArray(n); val orientation = FloatArray(n)
        var inputTool = InkStroke.TOOL_UNKNOWN
        for (i in 0 until n) {
            inputs.populate(i, scratch)
            xs[i] = scratch.x - pageLeft
            ys[i] = scratch.y - pageTop
            ts[i] = scratch.elapsedTimeMillis.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            pressure[i] = if (scratch.hasPressure) scratch.pressure else InkStroke.NONE
            tilt[i] = if (scratch.hasTilt) scratch.tiltRadians else InkStroke.NONE
            orientation[i] = if (scratch.hasOrientation) scratch.orientationRadians else InkStroke.NONE
            inputTool = toolCode(scratch.toolType)
        }
        return InkStroke(id, toolOf(stroke), stroke.brush.colorIntArgb, stroke.brush.size, xs, ys, ts, pressure, tilt, orientation, inputTool)
    }

    /** Builds a drawable stroke (page coordinates) from a saved one, or null if it has no valid points. */
    fun toDrawable(saved: InkStroke): Stroke? {
        val batch = MutableStrokeInputBatch()
        val type = toolType(saved.inputTool)
        var lastT = -1L
        for (i in 0 until saved.pointCount) {
            val t = maxOf(saved.times[i].toLong(), lastT) // times must never go backwards
            try {
                batch.add(
                    type = type,
                    x = saved.xs[i],
                    y = saved.ys[i],
                    elapsedTimeMillis = t,
                    pressure = saved.pressure[i].takeIf { it != InkStroke.NONE } ?: StrokeInput.NO_PRESSURE,
                    tiltRadians = saved.tilt[i].takeIf { it != InkStroke.NONE } ?: StrokeInput.NO_TILT,
                    orientationRadians = saved.orientation[i].takeIf { it != InkStroke.NONE } ?: StrokeInput.NO_ORIENTATION,
                )
                lastT = t
            } catch (e: IllegalArgumentException) {
                // A repeated or odd point: skip it rather than lose the whole stroke.
            }
        }
        if (batch.isEmpty()) return null
        return Stroke(brush(saved.tool, saved.colorArgb, saved.size), batch)
    }

    private fun toolCode(type: InputToolType): Int = when (type) {
        InputToolType.STYLUS -> InkStroke.TOOL_STYLUS
        InputToolType.TOUCH -> InkStroke.TOOL_TOUCH
        InputToolType.MOUSE -> InkStroke.TOOL_MOUSE
        else -> InkStroke.TOOL_UNKNOWN
    }

    private fun toolType(code: Int): InputToolType = when (code) {
        InkStroke.TOOL_STYLUS -> InputToolType.STYLUS
        InkStroke.TOOL_TOUCH -> InputToolType.TOUCH
        InkStroke.TOOL_MOUSE -> InputToolType.MOUSE
        else -> InputToolType.UNKNOWN
    }
}
