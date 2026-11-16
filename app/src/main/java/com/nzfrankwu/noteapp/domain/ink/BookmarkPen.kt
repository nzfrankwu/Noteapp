package com.nzfrankwu.noteapp.domain.ink

/**
 * The bookmark pen (PHASE-5.md FB5): the part of the page a free loop or a rectangle picks. The
 * picture and the bookmark's spot are the rectangle around what was drawn, kept on the page.
 */
object BookmarkPen {
    /** Smaller than this (page units, about 3 mm) on either side is a slip, not a selection. */
    const val MIN_SIDE = 8f

    /**
     * The rectangle [left, top, right, bottom] (page units) around the points [xs], [ys] (page
     * units), cut to the page ([pageWidth] × [pageHeight]); null when it's too small or off the page.
     */
    fun area(xs: FloatArray, ys: FloatArray, pageWidth: Float, pageHeight: Float): FloatArray? {
        if (xs.isEmpty() || xs.size != ys.size) return null
        val l = xs.min().coerceIn(0f, pageWidth)
        val r = xs.max().coerceIn(0f, pageWidth)
        val t = ys.min().coerceIn(0f, pageHeight)
        val b = ys.max().coerceIn(0f, pageHeight)
        if (r - l < MIN_SIDE || b - t < MIN_SIDE) return null
        return floatArrayOf(l, t, r, b)
    }

    /**
     * The points to keep while drawing: a rectangle keeps only its first corner and the pen's
     * point now; a free loop keeps every point.
     */
    fun extend(xs: List<Float>, ys: List<Float>, x: Float, y: Float, rect: Boolean): Pair<List<Float>, List<Float>> =
        if (rect && xs.isNotEmpty()) listOf(xs.first(), x) to listOf(ys.first(), y) else (xs + x) to (ys + y)

    /** The largest side (pixels) a bookmark picture is saved at. */
    const val MAX_PICTURE_SIDE = 1400
}
