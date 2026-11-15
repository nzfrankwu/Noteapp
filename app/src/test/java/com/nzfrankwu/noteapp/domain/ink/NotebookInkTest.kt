package com.nzfrankwu.noteapp.domain.ink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookInkTest {
    private val a = testStroke("a", 0f to 0f, 1f to 1f)
    private val b = testStroke("b", 2f to 2f, 3f to 3f)
    private val c = testStroke("c", 4f to 4f, 5f to 5f)

    private fun ids(ink: NotebookInk, page: String = "p") = ink.strokes(page).map { it.id }

    @Test
    fun drawing_thenUndo_thenRedo() {
        val ink = NotebookInk()
        ink.load("p", listOf(a))
        ink.add("p", listOf(b))
        assertEquals(listOf("a", "b"), ids(ink))

        assertEquals(InkEdit.Add("p", listOf(b)), ink.undo())
        assertEquals(listOf("a"), ids(ink))
        assertTrue(ink.canRedo)

        ink.redo()
        assertEquals(listOf("a", "b"), ids(ink))
    }

    @Test
    fun loadingSavedStrokes_isNotUndoable() {
        val ink = NotebookInk()
        ink.load("p", listOf(a, b))
        assertFalse(ink.canUndo)
        assertNull(ink.undo())
        assertEquals(listOf("a", "b"), ids(ink))
    }

    @Test
    fun undoingAnErase_putsStrokesBackInTheirOldOrder() {
        val ink = NotebookInk()
        ink.load("p", listOf(a, b, c))
        assertEquals(2, ink.erase("p", setOf("a", "c")))
        assertEquals(listOf("b"), ids(ink))
        ink.undo()
        assertEquals(listOf("a", "b", "c"), ids(ink))
    }

    @Test
    fun erasingNothing_recordsNothing() {
        val ink = NotebookInk()
        ink.load("p", listOf(a))
        assertEquals(0, ink.erase("p", setOf("zzz")))
        assertFalse(ink.canUndo)
    }

    @Test
    fun movingWithTheLasso_isUndoable() {
        val ink = NotebookInk()
        ink.load("p", listOf(a, b))
        val moved = b.translated(10f, 0f)
        ink.replace("p", listOf(moved))
        assertEquals(12f, ink.strokes("p")[1].xs[0])
        ink.undo()
        assertEquals(2f, ink.strokes("p")[1].xs[0])
        ink.redo()
        assertEquals(12f, ink.strokes("p")[1].xs[0])
    }

    @Test
    fun aNewEdit_clearsRedo() {
        val ink = NotebookInk()
        ink.add("p", listOf(a))
        ink.undo()
        ink.add("p", listOf(b))
        assertFalse(ink.canRedo)
        assertEquals(listOf("b"), ids(ink))
    }

    @Test
    fun pagesAreKeptApart() {
        val ink = NotebookInk()
        ink.add("p1", listOf(a))
        ink.add("p2", listOf(b))
        ink.undo()
        assertEquals(listOf("a"), ids(ink, "p1"))
        assertEquals(emptyList<String>(), ids(ink, "p2"))
    }

    @Test
    fun pageEdits_areReturnedForTheCallerToUndo() {
        val ink = NotebookInk()
        ink.recordPageEdit(InkEdit.AddPage("p9", 3))
        assertEquals(InkEdit.AddPage("p9", 3), ink.undo())
        assertEquals(InkEdit.AddPage("p9", 3), ink.redo())
    }

    @Test
    fun undoHistory_isCapped() {
        val ink = NotebookInk(maxUndo = 3)
        repeat(5) { ink.add("p", listOf(testStroke("s$it", 0f to 0f))) }
        var undone = 0
        while (ink.undo() != null) undone++
        assertEquals(3, undone)
        assertEquals(listOf("s0", "s1"), ids(ink))
    }
}
