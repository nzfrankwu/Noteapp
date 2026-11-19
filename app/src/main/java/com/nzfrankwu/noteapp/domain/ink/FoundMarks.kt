package com.nzfrankwu.noteapp.domain.ink

/**
 * Where to draw the short-lived mark around a search match on a page (19 Nov): the handwritten
 * line search found, or a text box. Plain Kotlin so it can be unit-tested.
 */
object FoundMarks {
    /** Space around the mark (page units), so it doesn't touch the writing. */
    const val PAD = 8f

    /**
     * The box (left, top, right, bottom, page units) around the handwritten line whose band is
     * [top]..[top]+[height]: the strokes whose middle is in that band. With none (the page
     * changed since it was read), the band across the page between the margins.
     */
    fun lineBox(strokeBounds: List<FloatArray>, top: Float, height: Float, pageWidth: Float): FloatArray {
        val bottom = top + height
        val inLine = strokeBounds.filter { b -> (b[1] + b[3]) / 2f in top..bottom }
        if (inLine.isEmpty()) return floatArrayOf(PAD * 3, top - PAD, pageWidth - PAD * 3, bottom + PAD)
        // Tall strokes (a bracket, a diagram line passing through) mustn't stretch the mark far.
        val reach = height * 0.75f
        return floatArrayOf(
            (inLine.minOf { it[0] } - PAD).coerceAtLeast(0f),
            (inLine.minOf { it[1] }.coerceAtLeast(top - reach)) - PAD,
            (inLine.maxOf { it[2] } + PAD).coerceAtMost(pageWidth),
            (inLine.maxOf { it[3] }.coerceAtMost(bottom + reach)) + PAD,
        )
    }

    /** The box around a text box's bounds. */
    fun around(bounds: FloatArray): FloatArray =
        floatArrayOf(bounds[0] - PAD, bounds[1] - PAD, bounds[2] + PAD, bounds[3] + PAD)
}
