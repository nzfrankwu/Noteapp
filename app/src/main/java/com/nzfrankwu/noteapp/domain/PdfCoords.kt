package com.nzfrankwu.noteapp.domain

/**
 * Turning our page units into a PDF page's own units and back (PHASE-4.md 4.6). Plain Kotlin.
 *
 * Our units run down and right from the top-left corner of the page AS SHOWN: its crop box,
 * turned by its /Rotate (as Android's PdfRenderer shows it). A PDF's own units run up and right
 * from the bottom-left of the unturned page.
 *
 * A mapping is [a, b, c, d, e, f], the PDF matrix form: x = a·u + c·v + e, y = b·u + d·v + f.
 */
object PdfCoords {
    /**
     * The mapping from our units to the PDF's for a crop box at ([x0], [y0]) of [width] × [height]
     * (PDF units, unturned) and a /Rotate of [rotation] degrees (any multiple of 90).
     */
    fun pageToPdf(x0: Float, y0: Float, width: Float, height: Float, rotation: Int): FloatArray =
        when (((rotation % 360) + 360) % 360) {
            90 -> floatArrayOf(0f, 1f, 1f, 0f, x0, y0)
            180 -> floatArrayOf(-1f, 0f, 0f, 1f, x0 + width, y0)
            270 -> floatArrayOf(0f, -1f, -1f, 0f, x0 + width, y0 + height)
            else -> floatArrayOf(1f, 0f, 0f, -1f, x0, y0 + height)
        }

    /** The opposite of [pageToPdf]: from the PDF's units to ours. */
    fun pdfToPage(x0: Float, y0: Float, width: Float, height: Float, rotation: Int): FloatArray =
        inverse(pageToPdf(x0, y0, width, height, rotation))

    /** Applies mapping [m] to the point ([x], [y]): returns (x', y'). */
    fun apply(m: FloatArray, x: Float, y: Float): Pair<Float, Float> =
        Pair(m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5])

    /** The inverse of an (invertible) mapping. */
    fun inverse(m: FloatArray): FloatArray {
        val (a, b, c, d, e) = m
        val f = m[5]
        val det = a * d - b * c
        require(det != 0f) { "Mapping can't be inverted" }
        val ia = d / det
        val ib = -b / det
        val ic = -c / det
        val id = a / det
        return floatArrayOf(ia, ib, ic, id, -(ia * e + ic * f), -(ib * e + id * f))
    }
}
