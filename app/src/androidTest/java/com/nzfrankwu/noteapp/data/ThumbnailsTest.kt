package com.nzfrankwu.noteapp.data

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThumbnailsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun line(color: Int) = InkStroke(
        "s", InkTool.PEN, color, 4f,
        floatArrayOf(100f, 300f), floatArrayOf(200f, 200f), intArrayOf(0, 100),
        floatArrayOf(0.5f, 0.5f), floatArrayOf(-1f, -1f), floatArrayOf(-1f, -1f),
    )

    @Test
    fun thePicture_isTheFirstPageWithItsWriting() {
        val thumbs = Thumbnails(tmp.root)
        thumbs.render("nb", PageTemplate.BLANK, Notebook.A4_WIDTH, Notebook.A4_HEIGHT, listOf(line(0xFF000000.toInt())))

        val bitmap = BitmapFactory.decodeFile(thumbs.file("nb").path)
        assertEquals(Thumbnails.WIDTH_PX.toInt(), bitmap.width)
        assertEquals((Notebook.A4_HEIGHT * Thumbnails.WIDTH_PX / Notebook.A4_WIDTH).toInt(), bitmap.height)
        val scale = Thumbnails.WIDTH_PX / Notebook.A4_WIDTH
        // On the line: ink. Far from it: white paper.
        assertNotEquals(0xFFFFFFFF.toInt(), bitmap.getPixel((200 * scale).toInt(), (200 * scale).toInt()))
        assertEquals(0xFFFFFFFF.toInt(), bitmap.getPixel(5, bitmap.height - 5))
        assertFalse("No temporary file is left", tmp.root.list()!!.any { it.endsWith(".tmp") })
    }

    @Test
    fun drawingAgain_replacesThePicture() {
        val thumbs = Thumbnails(tmp.root)
        thumbs.render("nb", PageTemplate.LINED, Notebook.A4_WIDTH, Notebook.A4_HEIGHT, emptyList())
        thumbs.render("nb", PageTemplate.LINED, Notebook.A4_WIDTH, Notebook.A4_HEIGHT, listOf(line(0xFFD32F2F.toInt())))
        assertTrue(thumbs.file("nb").exists())
        assertEquals(listOf("nb.png"), tmp.root.list()!!.toList())
    }
}
