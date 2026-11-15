package com.nzfrankwu.noteapp.ui.notebook

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushBehavior
import androidx.ink.brush.BrushCoat
import androidx.ink.brush.BrushPaint
import androidx.ink.brush.BrushTip
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

    /**
     * The round-tip highlighter (F18): the same highlighter (paint, behaviours), with its flat
     * chisel tip swapped for a round one. Its look is pinned by its own key, "highlighter-round-v1".
     */
    @OptIn(androidx.ink.brush.ExperimentalInkCustomBrushApi::class)
    private val roundHighlighterFamily: BrushFamily by lazy {
        val coats = highlighterFamily.coats.map { coat ->
            val t = coat.tip
            BrushCoat(
                BrushTip(
                    scaleX = 1f, scaleY = 1f, cornerRounding = 1f, slantDegrees = 0f, pinch = 0f, rotationDegrees = 0f,
                    particleGapDistanceScale = t.particleGapDistanceScale,
                    particleGapDurationMillis = t.particleGapDurationMillis,
                    behaviors = t.behaviors,
                ),
                coat.paintPreferences,
            )
        }
        highlighterFamily.copy(coats = coats, clientBrushFamilyId = "noteapp:highlighter-round-v1")
    }

    // Pens with less (or no) pressure (F22): a round tip whose size follows pressure between the
    // two multipliers. Pinned by their keys, like the stock brushes.
    private val steadyPenFamily: BrushFamily by lazy { roundPen("pen-steady-v1", null) }
    private val lowPressurePenFamily: BrushFamily by lazy { roundPen("pen-pressure-low-v1", 0.75f to 1.1f) }
    private val midPressurePenFamily: BrushFamily by lazy { roundPen("pen-pressure-mid-v1", 0.5f to 1.2f) }

    @OptIn(androidx.ink.brush.ExperimentalInkCustomBrushApi::class)
    private fun roundPen(key: String, sizeByPressure: Pair<Float, Float>?): BrushFamily {
        val behaviors = sizeByPressure?.let { (low, high) ->
            listOf(
                BrushBehavior(
                    BrushBehavior.Source.NORMALIZED_PRESSURE, BrushBehavior.Target.SIZE_MULTIPLIER,
                    0f, 1f, low, high, BrushBehavior.OutOfRange.CLAMP,
                ),
            )
        } ?: emptyList()
        return BrushFamily(BrushTip(behaviors = behaviors), BrushPaint(), "noteapp:$key")
    }

    fun family(tool: InkTool): BrushFamily = when (tool) {
        InkTool.PEN -> penFamily
        InkTool.HIGHLIGHTER -> highlighterFamily
        InkTool.HIGHLIGHTER_ROUND -> roundHighlighterFamily
        InkTool.PEN_STEADY -> steadyPenFamily
        InkTool.PEN_PRESSURE_LOW -> lowPressurePenFamily
        InkTool.PEN_PRESSURE_MID -> midPressurePenFamily
    }

    fun brush(tool: InkTool, colorArgb: Int, size: Float): Brush =
        Brush.createWithColorIntArgb(family(tool), if (tool.isHighlighter) seeThrough(colorArgb) else colorArgb, size, EPSILON)

    /**
     * Highlighter ink is see-through, so the writing and lines under it still show. A colour with
     * its own alpha keeps it (the opacity slider, F21); a fully opaque one gets the default.
     */
    private fun seeThrough(argb: Int): Int =
        if ((argb ushr 24) == 0xFF) (argb and 0x00FFFFFF) or (HIGHLIGHTER_ALPHA shl 24) else argb

    private const val HIGHLIGHTER_ALPHA = 0x66

    /** Which tool drew a finished stroke (by its brush). */
    fun toolOf(stroke: Stroke): InkTool = when (stroke.brush.family) {
        highlighterFamily -> InkTool.HIGHLIGHTER
        roundHighlighterFamily -> InkTool.HIGHLIGHTER_ROUND
        steadyPenFamily -> InkTool.PEN_STEADY
        lowPressurePenFamily -> InkTool.PEN_PRESSURE_LOW
        midPressurePenFamily -> InkTool.PEN_PRESSURE_MID
        else -> InkTool.PEN
    }

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
