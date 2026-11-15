package com.nzfrankwu.noteapp.domain

/**
 * Where a page template's lines and dots go, in page units (1/72 inch; see Notebook). Shared by
 * the notebook tiles, the editor and (Phase 6) PDF export, so they all look the same.
 */
object PagePattern {
    /** Gap between lines on lined paper: 24 units ≈ 8.5 mm, like college-ruled paper. */
    const val LINE_SPACING = 24f

    /** Lined paper starts this far down, leaving a header area like a real notebook. */
    const val LINED_TOP_MARGIN = 72f

    /** Lines stop this far from the left and right edges. */
    const val LINED_SIDE_MARGIN = 24f

    /** Square size on grid paper and dot spacing on dot-grid paper (≈ 7 mm). */
    const val GRID_SPACING = 20f

    /** The y positions of the lines of lined paper on a page [height] units tall. */
    fun linedRows(height: Float): List<Float> = generateSequence(LINED_TOP_MARGIN) { it + LINE_SPACING }
        .takeWhile { it < height - LINE_SPACING / 2 }
        .toList()

    /** Positions of grid lines (or dot rows/columns) across a [length]-unit side, centred on the page. */
    fun gridStops(length: Float): List<Float> {
        val count = (length / GRID_SPACING).toInt()
        val start = (length - count * GRID_SPACING) / 2f
        return (0..count).map { start + it * GRID_SPACING }.filter { it > 0f && it < length }
    }
}
