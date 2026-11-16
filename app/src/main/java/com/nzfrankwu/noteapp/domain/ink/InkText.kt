package com.nzfrankwu.noteapp.domain.ink

/**
 * A text box on a page (Phase 5): typed text drawn above the pictures and under the ink.
 *
 * The box is in page units (1/72 inch): [left], [top] and [width] are set by the user; the text
 * wraps to [width]. [height] is the height the text took when it was last laid out (the box grows
 * as you type), kept so hit-testing, the lasso and bookmarks don't need Android's text layout.
 * [size] is the font size in page units, [colorArgb] the colour, and [bold] applies to the whole box.
 */
data class InkText(
    val id: String,
    val text: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val size: Float = DEFAULT_SIZE,
    val colorArgb: Int = DEFAULT_COLOR,
    val bold: Boolean = false,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    /** The same box moved by ([dx], [dy]). */
    fun movedBy(dx: Float, dy: Float): InkText = copy(left = left + dx, top = top + dy)

    /**
     * The box scaled by [factor] about ([px], [py]): its place, width, height and text size all
     * scale, so the lines break in the same places (the lasso's resize).
     */
    fun scaledAbout(px: Float, py: Float, factor: Float): InkText = copy(
        left = px + (left - px) * factor,
        top = py + (top - py) * factor,
        width = (width * factor).coerceAtLeast(MIN_WIDTH),
        height = height * factor,
        size = (size * factor).coerceIn(MIN_SIZE, MAX_SIZE),
    )

    companion object {
        /** About 12 pt: normal body text on an A4 page. */
        const val DEFAULT_SIZE = 12f
        const val MIN_SIZE = 6f
        const val MAX_SIZE = 96f

        /** Black, like pen 1. */
        const val DEFAULT_COLOR = InkPalette.BLACK

        /** The narrowest a box can be dragged (page units). */
        const val MIN_WIDTH = 24f

        /** The longest text one box keeps (a whole essay doesn't belong in one box). */
        const val MAX_LENGTH = 20_000

        /** The sizes A− / A+ step through. */
        val SIZES = listOf(8f, 10f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 40f, 48f, 64f, 80f, 96f)

        /** The next size up from [size] (or the largest). */
        fun larger(size: Float): Float = SIZES.firstOrNull { it > size + 0.01f } ?: MAX_SIZE

        /** The next size down from [size] (or the smallest). */
        fun smaller(size: Float): Float = SIZES.lastOrNull { it < size - 0.01f } ?: SIZES.first()
    }
}
