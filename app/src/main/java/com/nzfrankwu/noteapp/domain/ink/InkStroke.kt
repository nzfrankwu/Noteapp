package com.nzfrankwu.noteapp.domain.ink

/**
 * The kinds of ink, saved by [key] in page files. A key must NEVER change or be removed once the
 * app is in use, or saved strokes would lose their look (StoredKeysTest freezes them). Each key
 * also pins the brush version (e.g. "pen-v1"), so a library update can't silently change how old
 * notes look: a new look gets a new key.
 */
enum class InkTool(val key: String) {
    /** Pressure-sensitive pen (Jetpack Ink's pressure pen, version 1). */
    PEN("pen-v1"),

    /** See-through highlighter with a flat, chisel tip (Jetpack Ink's highlighter, version 1). */
    HIGHLIGHTER("highlighter-v1"),

    /** See-through highlighter with a round tip (F18): Jetpack Ink's highlighter, version 1, with a round tip. */
    HIGHLIGHTER_ROUND("highlighter-round-v1"),

    /** Pen that ignores pressure: always the same width (F22). */
    PEN_STEADY("pen-steady-v1"),

    /** Pen whose width follows pressure a little (F22). */
    PEN_PRESSURE_LOW("pen-pressure-low-v1"),

    /** Pen whose width follows pressure halfway (F22). Older notes; new writing uses [PEN_PRESSURE_MID_V2]. */
    PEN_PRESSURE_MID("pen-pressure-mid-v1"),

    /**
     * Medium pressure, a little more like a real pen (7.12, the user's try on the tablet, 18 Nov):
     * lighter strokes thinner, harder ones thicker than [PEN_PRESSURE_MID].
     */
    PEN_PRESSURE_MID_V2("pen-pressure-mid-v2"),

    /**
     * Full pressure, a little more like a real pen (7.12): Jetpack Ink's pressure pen ([PEN]) with
     * its pressure response made stronger. [PEN] stays for the strokes already written with it.
     */
    PEN_PRESSURE_FULL_V2("pen-pressure-full-v2");

    /** Highlighter ink: see-through, and always drawn under the pen. */
    val isHighlighter: Boolean get() = this == HIGHLIGHTER || this == HIGHLIGHTER_ROUND

    companion object {
        /** Unknown keys (e.g. written by a newer app) fall back to [PEN], so the ink still shows. */
        fun fromKey(key: String): InkTool = entries.firstOrNull { it.key == key } ?: PEN

        /** The pen ink for a pressure level ([PenPressure]): 0 = steady width … 3 = full pressure pen. */
        fun pen(pressureLevel: Int): InkTool = when (pressureLevel) {
            PenPressure.OFF -> PEN_STEADY
            PenPressure.LOW -> PEN_PRESSURE_LOW
            PenPressure.MEDIUM -> PEN_PRESSURE_MID_V2
            else -> PEN_PRESSURE_FULL_V2
        }
    }
}

/**
 * One handwritten stroke, as saved: what drew it, and every input point in page units
 * (1/72 inch from the page's top-left corner).
 *
 * Point data is kept in plain arrays (one entry per point) to stay small for long notebooks.
 * [pressure], [tilt] and [orientation] use [NONE] when the pen didn't report them.
 */
class InkStroke(
    /** Permanent random ID, so undo, lasso and (later) backups can tell strokes apart. */
    val id: String,
    val tool: InkTool,
    /** Colour as 0xAARRGGBB. */
    val colorArgb: Int,
    /** Brush size in page units. */
    val size: Float,
    val xs: FloatArray,
    val ys: FloatArray,
    /** Milliseconds since the stroke started. */
    val times: IntArray,
    val pressure: FloatArray,
    val tilt: FloatArray,
    val orientation: FloatArray,
    /** What drew it: [TOOL_STYLUS], [TOOL_TOUCH], [TOOL_MOUSE] or [TOOL_UNKNOWN]. */
    val inputTool: Int = TOOL_STYLUS,
) {
    init {
        val n = xs.size
        require(ys.size == n && times.size == n && pressure.size == n && tilt.size == n && orientation.size == n) {
            "Every point array must have one entry per point"
        }
    }

    val pointCount: Int get() = xs.size

    private var cachedBounds: FloatArray? = null

    /**
     * The smallest box around the points (not counting the brush width), as [left, top, right,
     * bottom]. Worked out once and remembered (a stroke never changes); don't modify the array.
     */
    fun bounds(): FloatArray = cachedBounds ?: computeBounds().also { cachedBounds = it }

    private fun computeBounds(): FloatArray {
        if (pointCount == 0) return floatArrayOf(0f, 0f, 0f, 0f)
        var l = xs[0]; var t = ys[0]; var r = xs[0]; var b = ys[0]
        for (i in 1 until pointCount) {
            if (xs[i] < l) l = xs[i]
            if (xs[i] > r) r = xs[i]
            if (ys[i] < t) t = ys[i]
            if (ys[i] > b) b = ys[i]
        }
        return floatArrayOf(l, t, r, b)
    }

    /** A copy with every point moved by [dx], [dy] (page units). */
    fun translated(dx: Float, dy: Float): InkStroke = mapped(scale = 1f, originX = 0f, originY = 0f, dx = dx, dy = dy, scaleSize = false)

    /**
     * A copy scaled by [scale] around ([originX], [originY]), then moved by [dx], [dy]. With
     * [scaleSize] the brush size scales too, so resized handwriting keeps its proportions.
     */
    fun mapped(scale: Float, originX: Float, originY: Float, dx: Float, dy: Float, scaleSize: Boolean = true, newId: String = id): InkStroke {
        val nx = FloatArray(pointCount) { originX + (xs[it] - originX) * scale + dx }
        val ny = FloatArray(pointCount) { originY + (ys[it] - originY) * scale + dy }
        return InkStroke(
            newId, tool, colorArgb, if (scaleSize) size * scale else size,
            nx, ny, times.copyOf(), pressure.copyOf(), tilt.copyOf(), orientation.copyOf(), inputTool,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is InkStroke && id == other.id && tool == other.tool && colorArgb == other.colorArgb &&
            size == other.size && inputTool == other.inputTool && xs.contentEquals(other.xs) &&
            ys.contentEquals(other.ys) && times.contentEquals(other.times) &&
            pressure.contentEquals(other.pressure) && tilt.contentEquals(other.tilt) &&
            orientation.contentEquals(other.orientation)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "InkStroke($id, $tool, ${pointCount} points)"

    companion object {
        /** "Not reported" for pressure, tilt and orientation. */
        const val NONE = -1f

        const val TOOL_UNKNOWN = 0
        const val TOOL_STYLUS = 1
        const val TOOL_TOUCH = 2
        const val TOOL_MOUSE = 3
    }
}
