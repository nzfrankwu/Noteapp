package com.nzfrankwu.noteapp.domain.ink

import kotlin.math.hypot

/** The tools on the notebook toolbar. Saved by [key] (the last tool used). */
enum class EditorTool(val key: String, val label: String) {
    PEN_1("pen1", "Pen 1"),
    PEN_2("pen2", "Pen 2"),
    PEN_3("pen3", "Pen 3"),
    HIGHLIGHTER("highlighter", "Highlighter"),
    ERASER("eraser", "Eraser"),
    LASSO("lasso", "Lasso");

    /** Pens and the highlighter put ink down. */
    val draws: Boolean get() = this != ERASER && this != LASSO

    /** Which of the 3 pen slots, or null. */
    val penSlot: Int? get() = when (this) { PEN_1 -> 0; PEN_2 -> 1; PEN_3 -> 2; else -> null }

    companion object {
        fun fromKey(key: String?): EditorTool = entries.firstOrNull { it.key == key } ?: PEN_1
        fun pen(slot: Int): EditorTool = listOf(PEN_1, PEN_2, PEN_3)[slot]
    }
}

/** One pen slot's colour (0xAARRGGBB) and size (page units). */
data class PenStyle(val colorArgb: Int, val size: Float)

/**
 * The notebook toolbar's saved choices (PHASE-2.md, 2.7 and 2.10), with sensible defaults.
 * Sizes are in page units (1/72 inch).
 */
data class InkToolSettings(
    val tool: EditorTool = EditorTool.PEN_1,
    val pens: List<PenStyle> = listOf(
        PenStyle(InkPalette.BLACK, InkPalette.SIZES[1]),
        PenStyle(InkPalette.BLUE, InkPalette.SIZES[1]),
        PenStyle(InkPalette.RED, InkPalette.SIZES[1]),
    ),
    val highlighter: PenStyle = PenStyle(InkPalette.HIGHLIGHT_YELLOW, InkPalette.HIGHLIGHTER_SIZE),
    /** Eraser radius in page units. */
    val eraserRadius: Float = 6f,
    /** Press hard with the pen to erase (PHASE-2.md). */
    val pressureErase: Boolean = true,
    /** How hard counts as "hard", 0–1 of the pen's full pressure. */
    val pressureEraseLevel: Float = PressureErase.DEFAULT_LEVEL,
    /** Smoothing of finished strokes, 0 (off) to 100. */
    val stability: Int = 0,
    /** Hold the pen still at the end of a line to straighten it. */
    val holdToStraighten: Boolean = true,
    /** Where the toolbar was left, as fractions of the screen (0–1). */
    val toolbarX: Float = 0f,
    val toolbarY: Float = 0.12f,
    val toolbarCollapsed: Boolean = false,
    /** What a double-tap does (Settings → Notebooks). */
    val fitMode: FitMode = FitMode.DEFAULT,
    /** Debug builds only: a finger draws too (for the emulator). */
    val drawWithFinger: Boolean = false,
) {
    fun penStyle(tool: EditorTool): PenStyle? = tool.penSlot?.let { pens[it] } ?: if (tool == EditorTool.HIGHLIGHTER) highlighter else null

    fun withStyle(tool: EditorTool, style: PenStyle): InkToolSettings = when {
        tool.penSlot != null -> copy(pens = pens.toMutableList().also { it[tool.penSlot!!] = style })
        tool == EditorTool.HIGHLIGHTER -> copy(highlighter = style)
        else -> this
    }
}

/** The toolbar's colours and sizes. Ink colours are picked to read well on white paper. */
object InkPalette {
    const val BLACK = 0xFF1B1C1E.toInt()
    const val NAVY = 0xFF1A3A8F.toInt()
    const val BLUE = 0xFF2F6BDB.toInt()
    const val RED = 0xFFD32F2F.toInt()
    const val GREEN = 0xFF2E7D32.toInt()
    const val ORANGE = 0xFFEF6C00.toInt()
    const val PURPLE = 0xFF7B1FA2.toInt()
    const val GREY = 0xFF757575.toInt()

    /** The 8 pen swatches, in toolbar order. */
    val PEN_COLORS = listOf(BLACK, NAVY, BLUE, RED, GREEN, ORANGE, PURPLE, GREY)

    /** "More colours…" for pens. */
    val MORE_PEN_COLORS = listOf(
        0xFF5D4037.toInt(), 0xFF00838F.toInt(), 0xFF00695C.toInt(), 0xFF558B2F.toInt(),
        0xFFC2185B.toInt(), 0xFFAD1457.toInt(), 0xFF4527A0.toInt(), 0xFF283593.toInt(),
        0xFFF9A825.toInt(), 0xFF6D4C41.toInt(), 0xFF37474F.toInt(), 0xFF9E9E9E.toInt(),
    )

    const val HIGHLIGHT_YELLOW = 0xFFFFEB3B.toInt()
    const val HIGHLIGHT_GREEN = 0xFF76FF03.toInt()
    const val HIGHLIGHT_PINK = 0xFFFF4081.toInt()
    const val HIGHLIGHT_BLUE = 0xFF40C4FF.toInt()
    const val HIGHLIGHT_ORANGE = 0xFFFFAB40.toInt()
    const val HIGHLIGHT_PURPLE = 0xFFE040FB.toInt()

    /** The highlighter's swatches (Jetpack Ink's highlighter makes them see-through). */
    val HIGHLIGHTER_COLORS = listOf(HIGHLIGHT_YELLOW, HIGHLIGHT_GREEN, HIGHLIGHT_PINK, HIGHLIGHT_BLUE, HIGHLIGHT_ORANGE, HIGHLIGHT_PURPLE)

    /** Pen thickness presets (fine, medium, bold) in page units: about 0.3, 0.5 and 0.9 mm. */
    val SIZES = listOf(0.9f, 1.5f, 2.6f)

    /** The slider's range for pens. */
    const val MIN_SIZE = 0.4f
    const val MAX_SIZE = 8f

    const val HIGHLIGHTER_SIZE = 10f
    val HIGHLIGHTER_SIZES = listOf(6f, 10f, 16f)
}

/**
 * The stylus side button (PHASE-2.md 2.10): one click switches to the lasso, the next click goes
 * back to the tool you were using. Choosing a tool on the toolbar ends the "lasso by button".
 */
class PenButtonToggle {
    private var before: EditorTool? = null

    /** Called on a button click with the current tool; returns the tool to switch to. */
    fun click(current: EditorTool): EditorTool = if (current == EditorTool.LASSO) {
        (before ?: EditorTool.PEN_1).also { before = null }
    } else {
        before = current
        EditorTool.LASSO
    }

    /** The user picked a tool on the toolbar. */
    fun picked(tool: EditorTool) {
        if (tool != EditorTool.LASSO) before = null
    }
}

/**
 * Press hard to erase (PHASE-2.md): while drawing with a pen, pressure at or above the level
 * turns that pen-down into erasing. [level] is the user's sensitivity (0–1, higher = harder).
 */
object PressureErase {
    const val DEFAULT_LEVEL = 0.9f
    const val MIN_LEVEL = 0.6f
    const val MAX_LEVEL = 0.98f

    /** Pens that don't report pressure send 1.0 or 0; those never trigger erasing. */
    fun isHard(pressure: Float, level: Float, penReportsPressure: Boolean): Boolean =
        penReportsPressure && pressure < 1.0f && pressure >= level

    /** True once a pen-down has shown varying pressure (so it's a real pressure pen). */
    fun reportsPressure(pressures: List<Float>): Boolean = pressures.distinct().size > 1
}

/**
 * Hold to straighten (PHASE-2.md 2.7): a line that ends with the pen held still for a moment
 * becomes a straight line from its start to its end.
 */
object Straighten {
    /** How long the pen must stay still at the end, in milliseconds. */
    const val HOLD_MS = 450

    /** How far it may wander while "still", in page units (about 1 mm). */
    const val STILL_RADIUS = 3f

    /** Shorter lines aren't straightened (dots, ticks). */
    const val MIN_LENGTH = 20f

    /** True when [stroke] ends with a hold and is long enough to straighten. */
    fun wanted(stroke: InkStroke): Boolean {
        val n = stroke.pointCount
        if (n < 3) return false
        val endT = stroke.times[n - 1]
        if (endT - stroke.times[0] < HOLD_MS) return false
        val ex = stroke.xs[n - 1]
        val ey = stroke.ys[n - 1]
        // Every point in the last HOLD_MS stays near the end point…
        var i = n - 1
        while (i >= 0 && endT - stroke.times[i] <= HOLD_MS) {
            if (hypot(stroke.xs[i] - ex, stroke.ys[i] - ey) > STILL_RADIUS) return false
            i--
        }
        if (i < 0) return false
        // …and the line itself is long enough.
        return hypot(ex - stroke.xs[0], ey - stroke.ys[0]) >= MIN_LENGTH
    }

    /** The straight version: the start and end points, with the stroke's average pressure. */
    fun straightened(stroke: InkStroke): InkStroke {
        val n = stroke.pointCount
        val p = stroke.pressure.filter { it != InkStroke.NONE }.average().toFloat().takeIf { !it.isNaN() } ?: InkStroke.NONE
        val steps = 12
        val xs = FloatArray(steps + 1) { stroke.xs[0] + (stroke.xs[n - 1] - stroke.xs[0]) * it / steps }
        val ys = FloatArray(steps + 1) { stroke.ys[0] + (stroke.ys[n - 1] - stroke.ys[0]) * it / steps }
        return InkStroke(
            stroke.id, stroke.tool, stroke.colorArgb, stroke.size, xs, ys,
            IntArray(steps + 1) { it * 8 }, FloatArray(steps + 1) { p },
            FloatArray(steps + 1) { InkStroke.NONE }, FloatArray(steps + 1) { InkStroke.NONE }, stroke.inputTool,
        )
    }
}

/** Stability (PHASE-2.md 2.7): smooths a finished stroke's wobble. 0 leaves it untouched. */
object Smoothing {
    fun smoothed(stroke: InkStroke, stability: Int): InkStroke {
        val n = stroke.pointCount
        val radius = (stability.coerceIn(0, 100) / 25) // 0..4 neighbouring points each side
        if (radius == 0 || n < 3) return stroke
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        for (i in 0 until n) {
            if (i == 0 || i == n - 1) { xs[i] = stroke.xs[i]; ys[i] = stroke.ys[i]; continue }
            val r = minOf(radius, i, n - 1 - i)
            var sx = 0f; var sy = 0f
            for (j in i - r..i + r) { sx += stroke.xs[j]; sy += stroke.ys[j] }
            xs[i] = sx / (2 * r + 1); ys[i] = sy / (2 * r + 1)
        }
        return InkStroke(stroke.id, stroke.tool, stroke.colorArgb, stroke.size, xs, ys, stroke.times, stroke.pressure, stroke.tilt, stroke.orientation, stroke.inputTool)
    }
}
