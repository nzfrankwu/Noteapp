package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** 19 Nov: the mark around a search match in the editor. */
class FoundMarksTest {
    private val pad = FoundMarks.PAD

    @Test
    fun aLine_isTheStrokesInItsBand_only() {
        val line = listOf(floatArrayOf(100f, 200f, 140f, 230f), floatArrayOf(150f, 205f, 300f, 232f))
        val above = floatArrayOf(90f, 120f, 500f, 150f)
        val below = floatArrayOf(60f, 300f, 600f, 330f)
        val box = FoundMarks.lineBox(line + above + below, top = 200f, height = 32f, pageWidth = 800f)
        assertArrayEquals(floatArrayOf(100f - pad, 200f - pad, 300f + pad, 232f + pad), box, 0.01f)
    }

    @Test
    fun aTallStrokeThroughTheLine_onlyStretchesItALittle() {
        val line = listOf(floatArrayOf(100f, 200f, 300f, 232f), floatArrayOf(320f, 100f, 330f, 340f)) // a bracket
        val box = FoundMarks.lineBox(line, top = 200f, height = 32f, pageWidth = 800f)
        assertArrayEquals(floatArrayOf(100f - pad, 200f - 24f - pad, 330f + pad, 232f + 24f + pad), box, 0.01f)
    }

    @Test
    fun noStrokesThere_marksTheBandBetweenTheMargins() {
        val box = FoundMarks.lineBox(emptyList(), top = 200f, height = 30f, pageWidth = 800f)
        assertArrayEquals(floatArrayOf(pad * 3, 200f - pad, 800f - pad * 3, 230f + pad), box, 0.01f)
    }

    @Test
    fun theMark_staysOnThePageSideways() {
        val box = FoundMarks.lineBox(listOf(floatArrayOf(2f, 200f, 798f, 230f)), top = 200f, height = 30f, pageWidth = 800f)
        assertArrayEquals(floatArrayOf(0f, 200f - pad, 800f, 230f + pad), box, 0.01f)
    }
}
