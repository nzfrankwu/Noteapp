package com.nzfrankwu.noteapp.domain.ink

import com.nzfrankwu.noteapp.domain.PageSpot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextSelectionTest {
    // Two lines: "Maxwell's equations hold" at y 100–112, "in free space" at y 120–132.
    private val text = TextSelection.build(
        listOf(
            PdfWord("Maxwell's", 50f, 100f, 100f, 112f, 109f, 0) to true,
            PdfWord("equations", 104f, 100f, 154f, 112f, 109f, 0) to false,
            PdfWord("hold", 158f, 100f, 180f, 112f, 109f, 0) to false,
            PdfWord("in", 50f, 120f, 60f, 132f, 129f, 0) to true,
            PdfWord("free", 64f, 120f, 84f, 132f, 129f, 0) to false,
            PdfWord("space", 88f, 120f, 118f, 132f, 129f, 0) to false,
        ),
    )

    @Test
    fun build_numbersTheLines_andDropsBlankWords() {
        val built = TextSelection.build(
            listOf(
                PdfWord(" ", 0f, 0f, 1f, 1f, 1f, 0) to true,
                PdfWord("a", 0f, 0f, 1f, 1f, 1f, 0) to false,
                PdfWord("b", 0f, 2f, 1f, 3f, 3f, 0) to true,
            ),
        )
        assertEquals(listOf("a", "b"), built.words.map { it.text })
        assertEquals(listOf(0, 1), built.words.map { it.line })
        assertEquals(listOf(0, 0, 0, 1, 1, 1), text.words.map { it.line })
    }

    @Test
    fun wordAt_findsTheWordUnderTheFinger() {
        assertEquals(1, TextSelection.wordAt(text, 120f, 105f))
        assertEquals(4, TextSelection.wordAt(text, 70f, 126f))
        // Just outside a word, within the slop.
        assertEquals(2, TextSelection.wordAt(text, 182f, 106f))
        // Far from any text.
        assertNull(TextSelection.wordAt(text, 300f, 400f))
        assertNull(TextSelection.wordAt(PageText.EMPTY, 0f, 0f))
    }

    @Test
    fun closestWord_picksTheNearestLine_thenTheNearestWord() {
        assertEquals(2, TextSelection.closestWord(text, 500f, 90f)) // above and right: end of line 1
        assertEquals(3, TextSelection.closestWord(text, 0f, 200f)) // below and left: start of line 2
        assertEquals(4, TextSelection.closestWord(text, 75f, 125f))
        assertNull(TextSelection.closestWord(PageText.EMPTY, 0f, 0f))
    }

    @Test
    fun rangeBetween_isAlwaysInOrder() {
        assertEquals(TextRange(1, 4), TextRange.between(4, 1))
    }

    @Test
    fun copyText_joinsWordsWithSpaces_andLinesWithBreaks() {
        assertEquals("equations hold\nin free", TextSelection.copyText(text, TextRange(1, 4)))
        assertEquals("hold", TextSelection.copyText(text, TextRange(2, 2)))
    }

    @Test
    fun lineRects_oneBoxPerLine() {
        val rects = TextSelection.lineRects(text, TextRange(1, 4))
        assertEquals(2, rects.size)
        assertArrayEquals(floatArrayOf(104f, 100f, 180f, 112f), rects[0], 0.001f)
        assertArrayEquals(floatArrayOf(50f, 120f, 84f, 132f), rects[1], 0.001f)
    }

    @Test
    fun highlightMarks_runAcrossEachLine_asTallAsIt() {
        val marks = TextSelection.highlightMarks(text, TextRange(1, 4))
        assertEquals(listOf(LineMark(104f, 180f, 106f, 12f), LineMark(50f, 84f, 126f, 12f)), marks)
    }

    @Test
    fun underlineMarks_sitJustUnderTheBaseline() {
        val marks = TextSelection.underlineMarks(text, TextRange(3, 5), thickness = 1f)
        assertEquals(listOf(LineMark(50f, 118f, 129f + TextSelection.UNDERLINE_GAP + 0.5f, 1f)), marks)
    }

    @Test
    fun spotOf_coversTheWholeSelection() {
        assertEquals(PageSpot(50f, 100f, 130f, 32f), TextSelection.spotOf(text, TextRange(1, 4)))
    }

    @Test
    fun handles_sitAtTheStartAndEndWords() {
        assertArrayEquals(floatArrayOf(104f, 112f, 84f, 132f), TextSelection.handles(text, TextRange(1, 4)), 0.001f)
    }
}
