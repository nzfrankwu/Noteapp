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

/**
 * One pen slot's colour (0xAARRGGBB), size (page units) and how much its width follows pressure
 * ([PenPressure], F22). For the highlighter, the colour's alpha is its opacity (see
 * [HighlightOpacity], F21).
 */
data class PenStyle(val colorArgb: Int, val size: Float, val pressure: Int = PenPressure.FULL) {
    /** This style with colour [color]; a highlighter keeps its opacity. */
    fun withColour(color: Int, highlighter: Boolean): PenStyle =
        copy(colorArgb = if (highlighter) HighlightOpacity.keepOpacity(colorArgb, color) else color or OPAQUE)
}

private const val OPAQUE = 0xFF000000.toInt()

/** How much a pen's width follows pressure (F22): a slider with 4 stops. */
object PenPressure {
    const val OFF = 0
    const val LOW = 1
    const val MEDIUM = 2
    const val FULL = 3
    val LABELS = listOf("Off (same width)", "Low", "Medium", "Full")
}

/**
 * The highlighter's opacity (F21), kept in its colour's alpha so every stroke remembers it.
 * A fully opaque colour means "the default" ([DEFAULT], how highlighting always looked).
 */
object HighlightOpacity {
    const val DEFAULT = 0.4f
    const val MIN = 0.1f
    const val MAX = 0.9f

    fun of(argb: Int): Float = if ((argb ushr 24) == 0xFF) DEFAULT else (argb ushr 24) / 255f

    /** [argb] at [opacity] (MIN–MAX). */
    fun applied(argb: Int, opacity: Float): Int {
        val a = (opacity.coerceIn(MIN, MAX) * 255).toInt().coerceIn(1, 0xFE)
        return (argb and 0x00FFFFFF) or (a shl 24)
    }

    /** [newColor] with [current]'s opacity. */
    fun keepOpacity(current: Int, newColor: Int): Int =
        if ((current ushr 24) == 0xFF) newColor or OPAQUE else (newColor and 0x00FFFFFF) or (current and OPAQUE)

    /** Same colour, whatever the opacity. */
    fun sameColour(a: Int, b: Int): Boolean = (a and 0x00FFFFFF) == (b and 0x00FFFFFF)
}

/** Changing the toolbar's colour list (F23): reorder, delete, replace, add. Plain Kotlin, unit-tested. */
object PaletteEdit {
    const val MAX = 16

    /** Moves the colour at [index] by [by] places (−1 earlier, +1 later). */
    fun move(colors: List<Int>, index: Int, by: Int): List<Int> {
        val to = index + by
        if (index !in colors.indices || to !in colors.indices) return colors
        return colors.toMutableList().also { val c = it.removeAt(index); it.add(to, c) }
    }

    /** Removes the colour at [index]; the last colour can't be removed. */
    fun delete(colors: List<Int>, index: Int): List<Int> =
        if (colors.size <= 1 || index !in colors.indices) colors else colors.filterIndexed { i, _ -> i != index }

    fun replace(colors: List<Int>, index: Int, color: Int): List<Int> =
        if (index !in colors.indices) colors else colors.toMutableList().also { it[index] = color or OPAQUE }

    /** Adds [color] at the end (not twice; at most [MAX]). */
    fun add(colors: List<Int>, color: Int): List<Int> {
        val c = color or OPAQUE
        if (colors.any { HighlightOpacity.sameColour(it, c) }) return colors
        return (colors + c).takeLast(MAX)
    }
}

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
    /** The highlighter's tip: round, or flat like a chisel marker (the default) (F18). */
    val highlighterRound: Boolean = false,
    /** Eraser radius in page units. */
    val eraserRadius: Float = 6f,
    /** Press hard with the pen to erase (PHASE-2.md). */
    val pressureErase: Boolean = true,
    /** How hard counts as "hard", 0–1 of the pen's full pressure. */
    val pressureEraseLevel: Float = PressureErase.DEFAULT_LEVEL,
    /** How long a hard press must be held (roughly still) before it erases, in ms (F9). */
    val pressureEraseHoldMs: Int = PressureErase.DEFAULT_HOLD_MS,
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
    /** Colours picked with the colour wheel (F10), newest first, at most [InkPalette.MAX_CUSTOM]. */
    val customColors: List<Int> = emptyList(),
    /** The toolbar's colours for pens and for the highlighter, in order; editable (F23). */
    val penPalette: List<Int> = InkPalette.PEN_COLORS,
    val highlighterPalette: List<Int> = InkPalette.HIGHLIGHTER_COLORS,
) {
    /** The toolbar colours shown for [tool]. */
    fun palette(tool: EditorTool): List<Int> = if (tool == EditorTool.HIGHLIGHTER) highlighterPalette else penPalette

    fun withPalette(tool: EditorTool, colors: List<Int>): InkToolSettings =
        if (tool == EditorTool.HIGHLIGHTER) copy(highlighterPalette = colors) else copy(penPalette = colors)

    /** Remembers [color] as a custom colour (newest first; no repeats; oldest dropped). */
    fun withCustomColor(color: Int): InkToolSettings =
        copy(customColors = (listOf(color) + customColors.filter { it != color }).take(InkPalette.MAX_CUSTOM))

    /** The kind of ink [tool] puts down (null for the eraser and lasso). */
    fun inkTool(tool: EditorTool = this.tool): InkTool? = when {
        tool.penSlot != null -> InkTool.pen(pens[tool.penSlot!!].pressure)
        tool == EditorTool.HIGHLIGHTER -> if (highlighterRound) InkTool.HIGHLIGHTER_ROUND else InkTool.HIGHLIGHTER
        else -> null
    }

    /** Forgets a colour-wheel colour (F28). */
    fun withoutCustomColor(color: Int): InkToolSettings = copy(customColors = customColors.filter { it != color })

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

    /** How many colour-wheel colours are remembered (F10). */
    const val MAX_CUSTOM = 8

    const val HIGHLIGHTER_SIZE = 10f
    val HIGHLIGHTER_SIZES = listOf(6f, 10f, 16f)
}

/**
 * The stylus side button (PHASE-2.md 2.10): one click switches to the lasso, the next click goes
 * back to the tool you were using. Choosing a tool on the toolbar ends the "lasso by button".
 */
/**
 * Which hardware keys are a pen's side button (PHASE-2.md F2). Some pens send their button as a
 * pen button on the touch events; others, like the OPPO Pencil 2, send a key over Bluetooth
 * (`KEY_F21`). Android key codes are passed as plain numbers so this stays testable.
 */
object PenButtonKeys {
    const val KEYCODE_F21 = 334
    const val KEYCODE_STYLUS_BUTTON_PRIMARY = 308
    const val KEYCODE_STYLUS_BUTTON_SECONDARY = 309
    const val KEYCODE_STYLUS_BUTTON_TERTIARY = 310

    /** The Linux scan code for F21, for Android versions that don't name the key yet. */
    const val SCAN_F21 = 191

    fun isPenButton(keyCode: Int, scanCode: Int): Boolean =
        keyCode == KEYCODE_F21 || keyCode == KEYCODE_STYLUS_BUTTON_PRIMARY ||
            keyCode == KEYCODE_STYLUS_BUTTON_SECONDARY || keyCode == KEYCODE_STYLUS_BUTTON_TERTIARY ||
            scanCode == SCAN_F21
}

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
    const val MAX_LEVEL = 1.0f

    /** Hold a hard press this long (roughly still) to start erasing (F9); 0 = at once. */
    const val DEFAULT_HOLD_MS = 500
    const val MAX_HOLD_MS = 1_500

    /** How far the pen may drift while held "still", in page units (about 1.8 mm). */
    const val STILL_RADIUS = 5f

    /**
     * True when [pressure] counts as a hard press. A full 1.0 counts too: it's what the hardest
     * presses report. Pens that don't report pressure (a fixed 1.0, so [penReportsPressure] is
     * false) never trigger erasing.
     */
    fun isHard(pressure: Float, level: Float, penReportsPressure: Boolean): Boolean =
        penReportsPressure && pressure >= level

    /** True once a pen-down has shown varying pressure (so it's a real pressure pen). */
    fun reportsPressure(pressures: List<Float>): Boolean = pressures.distinct().size > 1

    /**
     * Whether a pen reports pressure, from its first sample and the current one (F26). Pens
     * without pressure send exactly 1.0 throughout; any other value, or a change, means real
     * pressure. So a hard press that never moves (or changes) still counts.
     */
    fun reportsPressure(first: Float, now: Float): Boolean = now != first || (now >= 0f && now < 1f)
}

/**
 * How long the pen has stayed roughly still: within [radius] of where it settled. Moving further
 * starts the count again from the new place. Used by press-hard-to-erase and hold-to-straighten.
 */
class StillTimer(private val radius: Float) {
    private var anchorX = 0f
    private var anchorY = 0f
    private var since = -1L

    fun reset() { since = -1L }

    /** Feeds the pen's position at [timeMs]; returns how long it has been still there (ms). */
    fun feed(x: Float, y: Float, timeMs: Long): Long {
        if (since < 0 || hypot(x - anchorX, y - anchorY) > radius) {
            anchorX = x; anchorY = y; since = timeMs
        }
        return timeMs - since
    }
}

/**
 * Press hard and hold to erase (PHASE-2.md F9): erasing starts once the pen has pressed hard and
 * stayed roughly still for [holdMs]. Easing off, or moving away, starts the wait again.
 */
class PressHold(val holdMs: Int, radius: Float = PressureErase.STILL_RADIUS) {
    private val still = StillTimer(radius)

    /** How long the current hard press has been held still (0 when not pressing hard). */
    var heldMs = 0L
        private set

    /** One pen sample (page units, ms). True once the hard, still press has lasted long enough. */
    fun feed(pressure: Float, level: Float, penReportsPressure: Boolean, x: Float, y: Float, timeMs: Long): Boolean {
        if (!PressureErase.isHard(pressure, level, penReportsPressure)) {
            still.reset()
            heldMs = 0L
            return false
        }
        heldMs = still.feed(x, y, timeMs)
        return heldMs >= holdMs
    }

    /**
     * True when a pen that's pressing hard has sent nothing new for [quietMs] (a pen held
     * perfectly still may go quiet) and that makes the hold long enough.
     */
    fun doneAfterQuiet(stillHard: Boolean, quietMs: Long): Boolean = stillHard && heldMs + quietMs >= holdMs
}

/**
 * Hold to straighten (PHASE-2.md 2.7, live since F11): hold the pen still for a moment while
 * drawing a line, and the line snaps straight from its start to the pen; keep moving the pen to
 * place the end, then lift. Near-horizontal, vertical and 45° lines snap to those angles.
 */
object Straighten {
    /** How long the pen must stay still before the line snaps straight, in milliseconds. */
    const val HOLD_MS = 450

    /** How far it may wander while "still", in page units (about 1 mm). */
    const val STILL_RADIUS = 3f

    /** Shorter lines aren't straightened (dots, ticks). */
    const val MIN_LENGTH = 20f

    /** Lines within this many degrees of a multiple of 45° snap to it (gently: F11). */
    const val SNAP_DEGREES = 4f

    /**
     * Where the line's end goes: ([ex], [ey]), or turned onto the nearest multiple of 45° around
     * the start ([sx], [sy]) when it's within [SNAP_DEGREES] of it (same length).
     */
    fun snappedEnd(sx: Float, sy: Float, ex: Float, ey: Float): Pair<Float, Float> {
        val dx = ex - sx
        val dy = ey - sy
        val length = hypot(dx, dy)
        if (length < 1e-3f) return ex to ey
        val angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble())
        val nearest = Math.round(angle / 45.0) * 45.0
        if (kotlin.math.abs(angle - nearest) > SNAP_DEGREES) return ex to ey
        val rad = Math.toRadians(nearest)
        return (sx + length * kotlin.math.cos(rad)).toFloat() to (sy + length * kotlin.math.sin(rad)).toFloat()
    }

    /**
     * A straight stroke from ([sx], [sy]) to ([ex], [ey]) in page units, with points every few
     * units and an even [pressure] (or [InkStroke.NONE]), so it draws like the pen.
     */
    fun line(
        id: String, tool: InkTool, colorArgb: Int, size: Float,
        sx: Float, sy: Float, ex: Float, ey: Float, pressure: Float, inputTool: Int,
    ): InkStroke {
        val steps = (hypot(ex - sx, ey - sy) / LINE_POINT_SPACING).toInt().coerceIn(2, 400)
        val xs = FloatArray(steps + 1) { sx + (ex - sx) * it / steps }
        val ys = FloatArray(steps + 1) { sy + (ey - sy) * it / steps }
        return InkStroke(
            id, tool, colorArgb, size, xs, ys,
            IntArray(steps + 1) { it * 4 }, FloatArray(steps + 1) { pressure },
            FloatArray(steps + 1) { InkStroke.NONE }, FloatArray(steps + 1) { InkStroke.NONE }, inputTool,
        )
    }

    private const val LINE_POINT_SPACING = 4f
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
