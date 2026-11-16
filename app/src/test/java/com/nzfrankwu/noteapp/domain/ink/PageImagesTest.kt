package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI

/** Pictures on pages (Phase 3, step 3.4): the page file format v2, undo, and picture geometry. */
class PageImagesTest {
    private val strokes = listOf(
        testStroke("a", 10f to 20f, 11f to 21.5f, 12.25f to 23f),
        testStroke("b", 100f to 100f, 140f to 100f, tool = InkTool.HIGHLIGHTER, size = 12f, withPressure = false),
    )
    private val photo = InkImage("i1", "images/photo.jpg", 100f, 200f, 300f, 225f)
    private val diagram = InkImage("i2", "images/diagram.png", 50f, 60f, 120f, 80f, rotation = 0.5f)

    // ---- Page file format ----

    @Test
    fun aPageWithoutPictures_isStillSavedAsVersion1_byteForByte() {
        val bytes = PageInkCodec.encode(strokes)
        assertArrayEquals(bytes, PageInkCodec.encode(strokes, emptyList()))
        assertEquals("Version byte", 1, bytes[5].toInt())
        assertEquals(PageContent(strokes), PageInkCodec.decodePage(bytes))
    }

    @Test
    fun aPageWithPictures_isSavedAsVersion2_andReadsBackExactly() {
        val bytes = PageInkCodec.encode(strokes, listOf(photo, diagram))
        assertEquals(2, bytes[5].toInt())
        assertEquals(PageContent(strokes, listOf(photo, diagram)), PageInkCodec.decodePage(bytes))
        // Reading just the strokes still works.
        assertEquals(strokes, PageInkCodec.decode(bytes))
        // Pictures alone, no strokes.
        assertEquals(PageContent(emptyList(), listOf(photo)), PageInkCodec.decodePage(PageInkCodec.encode(emptyList(), listOf(photo))))
    }

    @Test
    fun aVersion1File_fromPhase2_stillReads() {
        // Written the way Phase 2 wrote pages: version 1, strokes only.
        val v1 = PageInkCodec.encode(strokes)
        assertEquals(PageContent(strokes, emptyList()), PageInkCodec.decodePage(v1))
    }

    @Test
    fun aNewerFormat_isRefused_neverMisread() {
        val bytes = PageInkCodec.encode(strokes, listOf(photo))
        bytes[5] = (PageInkCodec.VERSION + 1).toByte()
        try {
            PageInkCodec.decodePage(bytes)
            fail("A newer format must be refused")
        } catch (e: PageFileException) {
            assertTrue(e.message!!.contains("newer"))
        }
    }

    @Test
    fun aDamagedVersion2File_isCaught() {
        val bytes = PageInkCodec.encode(strokes, listOf(photo, diagram))
        for (cut in listOf(bytes.size - 1, bytes.size / 2, 10)) {
            try {
                PageInkCodec.decodePage(bytes.copyOf(cut))
                fail("A file cut to $cut bytes must be refused")
            } catch (e: PageFileException) {
                // expected
            }
        }
        val changed = bytes.copyOf().also { it[it.size - 10] = (it[it.size - 10] + 1).toByte() }
        try {
            PageInkCodec.decodePage(changed)
            fail("A changed byte must be caught")
        } catch (e: PageFileException) {
            // expected
        }
    }

    @Test
    fun aVersion1File_withExtraPictureData_isRefused() {
        // Version 1 never has pictures: data after the strokes means the file isn't what it says.
        val v2 = PageInkCodec.encode(strokes, listOf(photo))
        val asV1 = v2.copyOf().also { it[5] = 1 }
        try {
            PageInkCodec.decodePage(asV1)
            fail("Must be refused")
        } catch (e: PageFileException) {
            // expected
        }
    }

    // ---- Undo and redo ----

    @Test
    fun addingPictures_undoesAndRedoes() {
        val ink = NotebookInk()
        ink.load("p", strokes)
        ink.addImages("p", listOf(photo))
        ink.addImages("p", listOf(diagram))
        assertEquals(listOf(photo, diagram), ink.images("p"))
        assertEquals(InkEdit.AddImages("p", listOf(diagram)), ink.undo())
        assertEquals(listOf(photo), ink.images("p"))
        ink.redo()
        assertEquals(listOf(photo, diagram), ink.images("p"))
        assertEquals(strokes, ink.strokes("p")) // the strokes are untouched
    }

    @Test
    fun removingStrokesAndPictures_isOneUndoStep_andPutsThemBackInOrder() {
        val ink = NotebookInk()
        ink.load("p", strokes, listOf(photo, diagram))
        assertEquals(2, ink.remove("p", setOf("a"), setOf("i1")))
        assertEquals(listOf("b"), ink.strokes("p").map { it.id })
        assertEquals(listOf(diagram), ink.images("p"))
        val undone = ink.undo()
        assertTrue(undone is InkEdit.Group)
        assertEquals(setOf("p"), undone!!.changedPages)
        assertEquals(strokes, ink.strokes("p"))
        assertEquals(listOf(photo, diagram), ink.images("p"))
        assertFalse(ink.canUndo)
        ink.redo()
        assertEquals(listOf(diagram), ink.images("p"))
    }

    @Test
    fun movingStrokesAndPictures_isOneUndoStep() {
        val ink = NotebookInk()
        ink.load("p", strokes, listOf(photo))
        val moved = photo.copy(left = 150f, top = 250f)
        val movedStroke = testStroke("a", 60f to 20f, 61f to 21.5f, 62.25f to 23f)
        ink.replace("p", listOf(movedStroke), listOf(moved))
        assertEquals(listOf(moved), ink.images("p"))
        assertEquals(movedStroke, ink.strokes("p")[0])
        ink.undo()
        assertEquals(listOf(photo), ink.images("p"))
        assertEquals(strokes[0], ink.strokes("p")[0])
    }

    @Test
    fun pictureOnlyAndStrokeOnlyEdits_areNotGrouped() {
        val ink = NotebookInk()
        ink.load("p", strokes, listOf(photo))
        ink.remove("p", emptySet(), setOf("i1"))
        assertTrue(ink.undo() is InkEdit.RemoveImages)
        ink.add("p", listOf(testStroke("c", 1f to 1f)), emptyList())
        assertTrue(ink.undo() is InkEdit.Add)
        assertEquals(0, ink.remove("p", setOf("nope"), setOf("nope")))
        assertFalse(ink.canUndo)
    }

    @Test
    fun theEraser_neverTakesPictures() {
        val ink = NotebookInk()
        ink.load("p", strokes, listOf(photo))
        ink.erase("p", setOf("a", "b", "i1"))
        assertEquals(listOf(photo), ink.images("p"))
    }

    // ---- Geometry ----

    @Test
    fun anUprightPicture_containsItsInside_only() {
        assertTrue(photo.contains(101f, 201f))
        assertTrue(photo.contains(399f, 424f))
        assertFalse(photo.contains(99f, 300f))
        assertFalse(photo.contains(250f, 426f))
        assertArrayEquals(floatArrayOf(100f, 200f, 400f, 425f), photo.bounds(), 0.001f)
    }

    @Test
    fun aTurnedPicture_turnsAboutItsCentre() {
        val square = InkImage("s", "images/s.png", 0f, 0f, 100f, 100f, rotation = (PI / 4).toFloat())
        // Turned 45°, its corners stick out to about 50 ± 70.7.
        val b = square.bounds()
        assertEquals(50f - 70.71f, b[0], 0.05f)
        assertEquals(50f + 70.71f, b[2], 0.05f)
        assertTrue(square.contains(50f, -15f)) // the top corner now points up
        assertFalse(square.contains(2f, 2f)) // the old corner is now outside
    }

    @Test
    fun aNewPicture_sitsInTheMiddle_atMost60PercentWide_andFitsTheHeight() {
        val wide = InkImage.centred("w", "images/w.jpg", 4000, 3000, 595f, 842f)
        assertEquals(595f * 0.6f, wide.width, 0.01f)
        assertEquals(wide.width * 0.75f, wide.height, 0.01f)
        assertEquals(595f / 2, wide.centreX, 0.01f)
        assertEquals(842f / 2, wide.centreY, 0.01f)
        val tall = InkImage.centred("t", "images/t.jpg", 1000, 5000, 842f, 595f)
        assertTrue(tall.height <= 595f * 0.8f + 0.01f)
        assertEquals(tall.height / 5f, tall.width, 0.01f)
    }
}
