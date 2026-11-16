package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Text boxes on pages (Phase 5, step 5.1): the page file format v3, undo, and box geometry. */
class PageTextsTest {
    private val strokes = listOf(
        testStroke("a", 10f to 20f, 11f to 21.5f, 12.25f to 23f),
        testStroke("b", 100f to 100f, 140f to 100f, tool = InkTool.HIGHLIGHTER, size = 12f, withPressure = false),
    )
    private val photo = InkImage("i1", "images/photo.jpg", 100f, 200f, 300f, 225f)
    private val note = InkText("t1", "Lecture 7: transmission lines", 40f, 60f, 300f, 16f)
    private val heading = InkText(
        "t2", "Ω = 2πf · résumé — 电磁 😀\nsecond line", 50f, 400f, 200f, 48f,
        size = 20f, colorArgb = 0xFFD32F2F.toInt(), bold = true,
    )

    // ---- Page file format ----

    @Test
    fun pagesWithoutText_areSavedExactlyAsBefore() {
        // No text boxes: the same bytes as Phase 2/3 wrote (version 1, or 2 with pictures).
        val v1 = PageInkCodec.encode(strokes)
        assertArrayEquals(v1, PageInkCodec.encode(strokes, emptyList(), emptyList()))
        assertArrayEquals(v1, PageInkCodec.encode(PageContent(strokes)))
        assertEquals(1, v1[5].toInt())
        val v2 = PageInkCodec.encode(strokes, listOf(photo))
        assertArrayEquals(v2, PageInkCodec.encode(strokes, listOf(photo), emptyList()))
        assertEquals(2, v2[5].toInt())
    }

    @Test
    fun aPageWithText_isSavedAsVersion3_andReadsBackExactly() {
        val content = PageContent(strokes, listOf(photo), listOf(note, heading))
        val bytes = PageInkCodec.encode(content)
        assertEquals(3, bytes[5].toInt())
        assertEquals(content, PageInkCodec.decodePage(bytes))
        assertEquals(strokes, PageInkCodec.decode(bytes))
    }

    @Test
    fun textWithoutPicturesOrStrokes_readsBack() {
        val only = PageContent(emptyList(), emptyList(), listOf(heading))
        assertEquals(only, PageInkCodec.decodePage(PageInkCodec.encode(only)))
    }

    @Test
    fun emptyAndVeryLongText_readBack() {
        // writeUTF would fail over 64 KB; the text is stored with its own length instead.
        val long = InkText("t3", "ü".repeat(InkText.MAX_LENGTH), 0f, 0f, 100f, 100f)
        val empty = InkText("t4", "", 0f, 0f, 100f, 10f)
        val content = PageContent(emptyList(), emptyList(), listOf(long, empty))
        assertEquals(content, PageInkCodec.decodePage(PageInkCodec.encode(content)))
    }

    @Test
    fun olderFormats_stillRead_withNoText() {
        assertEquals(PageContent(strokes), PageInkCodec.decodePage(PageInkCodec.encode(strokes)))
        assertEquals(PageContent(strokes, listOf(photo)), PageInkCodec.decodePage(PageInkCodec.encode(strokes, listOf(photo))))
    }

    @Test
    fun aDamagedVersion3File_isCaught() {
        val bytes = PageInkCodec.encode(PageContent(strokes, listOf(photo), listOf(note, heading)))
        for (cut in listOf(bytes.size - 1, bytes.size - 20, bytes.size / 2, 10)) {
            assertRefused(bytes.copyOf(cut))
        }
        assertRefused(bytes.copyOf().also { it[it.size - 12] = (it[it.size - 12] + 1).toByte() })
    }

    @Test
    fun aVersion2File_withExtraTextData_isRefused() {
        // Version 2 never has text boxes: data after the pictures means the file isn't what it says.
        val v3 = PageInkCodec.encode(PageContent(strokes, listOf(photo), listOf(note)))
        assertRefused(v3.copyOf().also { it[5] = 2 })
    }

    @Test
    fun aNewerFormat_isRefused() {
        val bytes = PageInkCodec.encode(PageContent(strokes, emptyList(), listOf(note)))
        bytes[5] = (PageInkCodec.VERSION + 1).toByte()
        try {
            PageInkCodec.decodePage(bytes)
            fail("Must be refused")
        } catch (e: PageFileException) {
            assertTrue(e.message!!.contains("newer"))
        }
    }

    private fun assertRefused(bytes: ByteArray) {
        try {
            PageInkCodec.decodePage(bytes)
            fail("Must be refused")
        } catch (e: PageFileException) {
            // expected
        }
    }

    // ---- Undo and redo ----

    @Test
    fun loadedText_isKept_andInContent() {
        val ink = NotebookInk()
        ink.load("p", strokes, listOf(photo), listOf(note))
        assertEquals(listOf(note), ink.texts("p"))
        assertEquals(PageContent(strokes, listOf(photo), listOf(note)), ink.content("p"))
    }

    @Test
    fun aNewBox_isOneUndoStep() {
        val ink = NotebookInk()
        ink.load("p", strokes)
        val edit = ink.commitText("p", before = null, after = note)
        assertTrue(edit is InkEdit.AddTexts)
        assertEquals(listOf(note), ink.texts("p"))
        assertEquals(setOf("p"), ink.undo()!!.changedPages)
        assertEquals(emptyList<InkText>(), ink.texts("p"))
        ink.redo()
        assertEquals(listOf(note), ink.texts("p"))
    }

    @Test
    fun aNewBoxLeftBlank_isNotAdded() {
        val ink = NotebookInk()
        ink.load("p", strokes)
        assertNull(ink.commitText("p", null, note.copy(text = "  \n ")))
        assertNull(ink.commitText("p", null, null))
        assertEquals(emptyList<InkText>(), ink.texts("p"))
        assertTrue(!ink.canUndo)
    }

    @Test
    fun editingABox_replacesItInPlace_andUndoes() {
        val ink = NotebookInk()
        ink.load("p", strokes, texts = listOf(note, heading))
        val typed = note.copy(text = note.text + " (cont.)", height = 32f, size = 14f, bold = true)
        assertTrue(ink.commitText("p", note, typed) is InkEdit.ReplaceTexts)
        assertEquals(listOf(typed, heading), ink.texts("p"))
        ink.undo()
        assertEquals(listOf(note, heading), ink.texts("p"))
    }

    @Test
    fun anUnchangedBox_recordsNothing() {
        val ink = NotebookInk()
        ink.load("p", strokes, texts = listOf(note))
        assertNull(ink.commitText("p", note, note))
        assertTrue(!ink.canUndo)
    }

    @Test
    fun emptyingABox_removesIt_andUndoPutsItBackInPlace() {
        val ink = NotebookInk()
        ink.load("p", strokes, texts = listOf(note, heading))
        assertTrue(ink.commitText("p", note, note.copy(text = "")) is InkEdit.RemoveTexts)
        assertEquals(listOf(heading), ink.texts("p"))
        ink.undo()
        assertEquals(listOf(note, heading), ink.texts("p"))
    }

    @Test
    fun lassoRemoveReplaceAndAdd_includeText() {
        val ink = NotebookInk()
        ink.load("p", strokes, listOf(photo), listOf(note, heading))
        val moved = note.movedBy(10f, 5f)
        ink.replace("p", emptyList(), emptyList(), listOf(moved))
        assertEquals(listOf(moved, heading), ink.texts("p"))
        assertEquals(2, ink.remove("p", setOf("a"), emptySet(), setOf("t2")))
        assertEquals(listOf(moved), ink.texts("p"))
        ink.undo() // one step brings back both the stroke and the box
        assertEquals(listOf(moved, heading), ink.texts("p"))
        assertEquals(strokes, ink.strokes("p"))
        val copy = heading.copy(id = "t9")
        ink.add("p", emptyList(), emptyList(), listOf(copy))
        assertEquals(listOf(moved, heading, copy), ink.texts("p"))
        ink.undo()
        ink.undo()
        assertEquals(listOf(note, heading), ink.texts("p"))
    }

    // ---- Geometry and sizes ----

    @Test
    fun containsAndMove() {
        assertTrue(note.contains(40f, 60f))
        assertTrue(note.contains(340f, 76f))
        assertTrue(!note.contains(341f, 70f))
        assertEquals(note.copy(left = 50f, top = 55f), note.movedBy(10f, -5f))
    }

    @Test
    fun scaling_scalesPlaceSizeAndWidth() {
        val big = note.scaledAbout(40f, 60f, 2f)
        assertEquals(note.copy(width = 600f, height = 32f, size = 24f), big)
        // Text never gets smaller than MIN_SIZE or wider than allowed limits break.
        assertEquals(InkText.MIN_SIZE, note.scaledAbout(0f, 0f, 0.01f).size, 0f)
        assertEquals(InkText.MIN_WIDTH, note.scaledAbout(0f, 0f, 0.01f).width, 0f)
    }

    // ---- The lasso (5.4) ----

    @Test
    fun theLassoLoop_picksABoxItTouchesOrSurrounds() {
        // note: 40..340 × 60..76
        val around = floatArrayOf(30f, 350f, 350f, 30f) to floatArrayOf(50f, 50f, 90f, 90f)
        assertTrue(InkGeometry.textInLoop(note, around.first, around.second))
        val crossing = floatArrayOf(100f, 120f, 120f, 100f) to floatArrayOf(40f, 40f, 100f, 100f)
        assertTrue(InkGeometry.textInLoop(note, crossing.first, crossing.second))
        val inside = floatArrayOf(100f, 120f, 110f) to floatArrayOf(65f, 65f, 70f)
        assertTrue(InkGeometry.textInLoop(note, inside.first, inside.second))
        val away = floatArrayOf(400f, 450f, 450f, 400f) to floatArrayOf(400f, 400f, 450f, 450f)
        assertTrue(!InkGeometry.textInLoop(note, away.first, away.second))
    }

    @Test
    fun selectionBounds_includeTextBoxes() {
        val box = InkGeometry.boundsOf(emptyList(), emptyList(), listOf(note, heading))!!
        assertArrayEquals(floatArrayOf(40f, 60f, 340f, 448f), box, 0.001f)
    }

    @Test
    fun movingTheSelection_movesTheBox() {
        val from = floatArrayOf(40f, 60f, 340f, 76f)
        val moved = LassoBox.mapText(note, from, LassoBox.moved(from, 15f, 30f))
        assertEquals(note.copy(left = 55f, top = 90f), moved)
    }

    @Test
    fun scalingTheSelection_scalesTheTextEvenly() {
        val from = floatArrayOf(40f, 60f, 340f, 76f)
        val to = floatArrayOf(40f, 60f, 640f, 92f) // twice as big, from the top-left corner
        val big = LassoBox.mapText(note, from, to)
        assertEquals(24f, big.size, 0.001f)
        assertEquals(600f, big.width, 0.001f)
        assertEquals(40f, big.left, 0.001f)
        assertEquals(60f, big.top, 0.001f)
    }

    @Test
    fun turningTheSelection_keepsTextUpright() {
        val from = floatArrayOf(40f, 60f, 340f, 76f)
        val turned = LassoBox.mapText(note, from, from, angle = 1.2f)
        // Its centre is the selection's centre, so it stays put, and the box isn't tilted.
        assertEquals(note.left, turned.left, 0.01f)
        assertEquals(note.top, turned.top, 0.01f)
        assertEquals(note.size, turned.size, 0f)
    }

    @Test
    fun sizeSteps() {
        assertEquals(14f, InkText.larger(12f), 0f)
        assertEquals(10f, InkText.smaller(12f), 0f)
        assertEquals(14f, InkText.larger(13f), 0f)
        assertEquals(12f, InkText.smaller(13f), 0f)
        assertEquals(InkText.MAX_SIZE, InkText.larger(InkText.MAX_SIZE), 0f)
        assertEquals(8f, InkText.smaller(8f), 0f)
    }
}
