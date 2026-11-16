package com.nzfrankwu.noteapp.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.domain.ink.InkText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** How text boxes are laid out (Phase 5, step 5.2): Android's text layout, so on the emulator. */
@RunWith(AndroidJUnit4::class)
class TextBoxLayoutTest {
    @Before
    fun install() {
        TextBoxLayout.install(InstrumentationRegistry.getInstrumentation().targetContext.assets)
    }

    private fun box(text: String, width: Float = 200f, size: Float = 12f, bold: Boolean = false) =
        InkText("t", text, 30f, 40f, width, 0f, size = size, bold = bold)

    @Test
    fun theBundledFont_loads_andCanBeEmbedded() {
        assertNotSame(Typeface.SANS_SERIF, TextBoxLayout.typeface)
        val bytes = TextBoxLayout.fontBytes()
        assertNotNull(bytes)
        assertTrue("A TrueType font", bytes!!.size > 100_000)
    }

    @Test
    fun oneLine_hasAboutTheFontsLineHeight() {
        val h = TextBoxLayout.height(box("Hello"))
        assertTrue("Height $h", h in 12f..18f)
        // An empty box is as tall as one line, so there's something to type into.
        assertEquals(h, TextBoxLayout.height(box("")), 0.01f)
    }

    @Test
    fun longText_wrapsAtTheWidth_andGrowsDownwards() {
        val text = "The characteristic impedance of a lossless line depends only on L and C per unit length."
        val one = TextBoxLayout.height(box("Hello"))
        val narrow = TextBoxLayout.height(box(text, width = 120f))
        val wide = TextBoxLayout.height(box(text, width = 600f))
        assertEquals(one, wide, 0.01f)
        assertTrue("Narrow $narrow vs one line $one", narrow > 3 * one)
        val lines = TextBoxLayout.lines(box(text, width = 120f))
        assertTrue(lines.size >= 4)
        // Every line fits in the box and starts at its left edge; the words are all there, in order.
        for (l in lines) {
            assertTrue("${l.text}: ${l.width}", l.width <= 120.5f)
            assertEquals(30f, l.x, 0.5f)
        }
        assertEquals(text, lines.joinToString(" ") { it.text })
        // Baselines go down the box.
        assertTrue(lines.zipWithNext().all { (a, b) -> b.baseline > a.baseline })
        assertTrue(lines.first().baseline > 40f && lines.last().baseline < 40f + narrow)
    }

    @Test
    fun newLines_startNewLines() {
        val lines = TextBoxLayout.lines(box("one\ntwo\n\nfour"))
        assertEquals(listOf("one", "two", "four"), lines.map { it.text })
    }

    @Test
    fun boldBreaksTheSameWay() {
        val text = "Faraday's law: the induced EMF equals the rate of change of flux linkage."
        val normal = TextBoxLayout.lines(box(text, width = 150f))
        val bold = TextBoxLayout.lines(box(text, width = 150f, bold = true))
        assertEquals(normal.map { it.text }, bold.map { it.text })
    }

    @Test
    fun biggerText_isTaller() {
        val small = TextBoxLayout.height(box("Hi", size = 12f))
        val big = TextBoxLayout.height(box("Hi", size = 24f))
        assertEquals(2f, big / small, 0.1f)
    }

    @Test
    fun drawing_putsInkInsideTheBox() {
        val b = TextBoxLayout.measured(box("Hello world", size = 20f).copy(colorArgb = 0xFF000000.toInt()))
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        TextBoxLayout.drawAll(Canvas(bitmap), listOf(b), 1f)
        var inside = 0
        var outside = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (bitmap.getPixel(x, y) ushr 24 > 128) {
                if (x >= b.left - 1 && x <= b.right + 1 && y >= b.top - 1 && y <= b.bottom + 1) inside++ else outside++
            }
        }
        assertTrue("Ink drawn: $inside", inside > 100)
        assertEquals(0, outside)
    }
}
