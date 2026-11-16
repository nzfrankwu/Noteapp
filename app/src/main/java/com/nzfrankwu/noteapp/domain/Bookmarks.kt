package com.nzfrankwu.noteapp.domain

import java.time.Instant

/** A rectangle on a page, in page units (1/72 inch): the spot a bookmark points at. */
@kotlinx.serialization.Serializable
data class PageSpot(val left: Float, val top: Float, val width: Float, val height: Float) {
    val isValid: Boolean
        get() = listOf(left, top, width, height).all { it.isFinite() } && width > 0f && height > 0f
}

/** One bookmark as the task screen lists it. */
data class BookmarkEntry(
    val id: String,
    val notebookId: String,
    val notebookTitle: String,
    val pageId: String,
    /** The page's number now (1 = the first page). */
    val pageNumber: Int,
    val note: String?,
    /** The spot on the page, or null for the whole page. */
    val spot: PageSpot?,
    val createdAt: Instant,
    /** The bookmark pen's picture (a file name in the notebook's folder), or null (PHASE-5.md FB5). */
    val image: String? = null,
)

/** A notebook's bookmarks for one task, in page order. */
data class BookmarkGroup(val notebookId: String, val notebookTitle: String, val bookmarks: List<BookmarkEntry>)

/** Ordering and tidying of page bookmarks (PHASE-4.md). Plain Kotlin, unit-tested. */
object Bookmarks {
    /** The longest note kept. */
    const val MAX_NOTE_LENGTH = 1000

    /** The longest note prefilled from selected PDF text (it can be edited before saving). */
    const val MAX_PREFILLED_NOTE_LENGTH = 300

    /**
     * Groups bookmarks by notebook (notebooks A–Z, ignoring case) and orders each group by page
     * number, smallest first. On the same page: a whole-page bookmark first, then spots from top
     * to bottom and left to right, then the oldest first.
     */
    fun group(entries: List<BookmarkEntry>): List<BookmarkGroup> {
        val inPage = compareBy<BookmarkEntry> { it.pageNumber }
            .thenBy { it.spot != null }
            .thenBy { it.spot?.top ?: 0f }
            .thenBy { it.spot?.left ?: 0f }
            .thenBy { it.createdAt }
            .thenBy { it.id }
        return entries.groupBy { it.notebookId }
            .map { (notebookId, list) -> BookmarkGroup(notebookId, list.first().notebookTitle, list.sortedWith(inPage)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { g: BookmarkGroup -> g.notebookTitle }.thenBy { it.notebookId })
    }

    /** A note as saved: trimmed, at most [MAX_NOTE_LENGTH] characters, and null when empty. */
    fun cleanNote(note: String?): String? = note?.trim()?.take(MAX_NOTE_LENGTH)?.trim()?.ifEmpty { null }

    /**
     * The note prefilled from selected text: runs of spaces and line breaks become one space,
     * and long text is cut at a word with "…".
     */
    fun noteFromSelectedText(text: String): String {
        val flat = text.trim().replace(Regex("\\s+"), " ")
        if (flat.length <= MAX_PREFILLED_NOTE_LENGTH) return flat
        val cut = flat.take(MAX_PREFILLED_NOTE_LENGTH - 1)
        val atWord = cut.lastIndexOf(' ').takeIf { it > MAX_PREFILLED_NOTE_LENGTH / 2 } ?: cut.length
        return cut.take(atWord).trimEnd() + "…"
    }

    /** "p. 12" */
    fun pageLabel(pageNumber: Int): String = "p. $pageNumber"
}
