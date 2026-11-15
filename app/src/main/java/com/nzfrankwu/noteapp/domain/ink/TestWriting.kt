package com.nzfrankwu.noteapp.domain.ink

import com.nzfrankwu.noteapp.domain.PagePattern
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Makes a page of pretend handwriting (rows of small squiggles in "words"), for checking that a
 * long, full notebook stays smooth (PHASE-2.md 2.12). Used only by the debug menu and tests.
 */
object TestWriting {
    /** About 900 letter-sized strokes filling a lined page; the same [seed] gives the same page. */
    fun page(pageWidth: Float, pageHeight: Float, seed: Int, newId: (Int) -> String = { "test-$seed-$it" }): List<InkStroke> {
        val random = Random(seed)
        val strokes = ArrayList<InkStroke>()
        for (rowY in PagePattern.linedRows(pageHeight)) {
            var x = PagePattern.LINED_SIDE_MARGIN + 6f + random.nextFloat() * 20f
            var lettersLeftInWord = 2 + random.nextInt(7)
            while (x < pageWidth - PagePattern.LINED_SIDE_MARGIN - 14f) {
                val width = 6f + random.nextFloat() * 6f
                val height = 6f + random.nextFloat() * 8f
                val points = 12 + random.nextInt(10)
                val turns = 1f + random.nextFloat() * 1.5f
                val xs = FloatArray(points) { x + width * it / (points - 1) + 1.5f * cos(it * turns) }
                val ys = FloatArray(points) { rowY - 2f - height / 2 + (height / 2) * sin(it * turns) }
                strokes += InkStroke(
                    newId(strokes.size), InkTool.PEN, if (random.nextInt(40) == 0) InkPalette.RED else InkPalette.NAVY,
                    InkPalette.SIZES[1], xs, ys,
                    IntArray(points) { it * 8 }, FloatArray(points) { 0.35f + 0.3f * random.nextFloat() },
                    FloatArray(points) { InkStroke.NONE }, FloatArray(points) { InkStroke.NONE },
                )
                x += width + 2.5f
                if (--lettersLeftInWord == 0) {
                    x += 9f
                    lettersLeftInWord = 2 + random.nextInt(7)
                }
            }
        }
        return strokes
    }
}
