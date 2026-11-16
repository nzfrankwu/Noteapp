package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BookmarksTest {
    private fun entry(
        id: String,
        page: Int,
        notebook: String = "n1",
        title: String = "Lecture",
        spot: PageSpot? = null,
        at: Long = 0,
    ) = BookmarkEntry(id, notebook, title, "p-$id", page, null, spot, Instant.ofEpochSecond(at))

    @Test
    fun group_ordersPagesSmallestFirst() {
        val groups = Bookmarks.group(listOf(entry("a", 12), entry("b", 3), entry("c", 7)))
        assertEquals(listOf("b", "c", "a"), groups.single().bookmarks.map { it.id })
    }

    @Test
    fun group_ordersNotebooksAToZ_ignoringCase() {
        val groups = Bookmarks.group(
            listOf(entry("a", 1, "n1", "lecture"), entry("b", 1, "n2", "Assignment"), entry("c", 2, "n1", "lecture")),
        )
        assertEquals(listOf("Assignment", "lecture"), groups.map { it.notebookTitle })
        assertEquals(listOf("a", "c"), groups[1].bookmarks.map { it.id })
    }

    @Test
    fun samePage_wholePageFirst_thenSpotsTopToBottom_thenOldest() {
        val groups = Bookmarks.group(
            listOf(
                entry("low", 5, spot = PageSpot(0f, 500f, 10f, 10f)),
                entry("high", 5, spot = PageSpot(0f, 100f, 10f, 10f)),
                entry("new", 5, at = 20),
                entry("old", 5, at = 10),
            ),
        )
        assertEquals(listOf("old", "new", "high", "low"), groups.single().bookmarks.map { it.id })
    }

    @Test
    fun sameTitles_stayInSeparateGroups() {
        val groups = Bookmarks.group(listOf(entry("a", 1, "n1", "Notes"), entry("b", 1, "n2", "Notes")))
        assertEquals(2, groups.size)
    }

    @Test
    fun cleanNote_trimsAndDropsEmpty_andCaps() {
        assertEquals("hi", Bookmarks.cleanNote("  hi \n"))
        assertNull(Bookmarks.cleanNote("   "))
        assertNull(Bookmarks.cleanNote(null))
        assertEquals("line 1\nline 2", Bookmarks.cleanNote("line 1\nline 2"))
        assertEquals(Bookmarks.MAX_NOTE_LENGTH, Bookmarks.cleanNote("x".repeat(5000))!!.length)
    }

    @Test
    fun noteFromSelectedText_flattensLineBreaks() {
        assertEquals("Maxwell's equations in free space", Bookmarks.noteFromSelectedText("  Maxwell's equations\nin  free\tspace "))
    }

    @Test
    fun noteFromSelectedText_cutsLongTextAtAWord() {
        val long = (1..200).joinToString(" ") { "word$it" }
        val note = Bookmarks.noteFromSelectedText(long)
        assertTrue(note.length <= Bookmarks.MAX_PREFILLED_NOTE_LENGTH)
        assertTrue(note.endsWith("…"))
        assertTrue(long.startsWith(note.removeSuffix("…")))
        assertFalse(note.removeSuffix("…").endsWith(" "))
    }

    @Test
    fun spotValidity() {
        assertTrue(PageSpot(0f, 0f, 1f, 1f).isValid)
        assertFalse(PageSpot(0f, 0f, 0f, 1f).isValid)
        assertFalse(PageSpot(Float.NaN, 0f, 1f, 1f).isValid)
    }

    @Test
    fun pageLabel() = assertEquals("p. 12", Bookmarks.pageLabel(12))
}
