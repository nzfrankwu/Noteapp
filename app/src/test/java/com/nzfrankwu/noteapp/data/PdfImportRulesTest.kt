package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.domain.PageSize
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.ui.folder.FolderContents
import com.nzfrankwu.noteapp.ui.folder.LibraryFilter
import com.nzfrankwu.noteapp.ui.folder.LibraryItem
import com.nzfrankwu.noteapp.ui.folder.itemsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Plain rules behind PDF import (Phase 3, step 3.3). */
class PdfImportRulesTest {
    @Test
    fun title_comesFromTheFileName_withoutPdf() {
        assertEquals("Lecture 3", PdfImport.titleFrom("Lecture 3.pdf"))
        assertEquals("Lecture 3", PdfImport.titleFrom("  Lecture 3 .PDF "))
        assertEquals("notes.v2", PdfImport.titleFrom("notes.v2"))
        assertEquals(PdfImport.DEFAULT_TITLE, PdfImport.titleFrom(null))
        assertEquals(PdfImport.DEFAULT_TITLE, PdfImport.titleFrom(".pdf"))
        assertEquals(120, PdfImport.titleFrom("x".repeat(500) + ".pdf").length)
    }

    @Test
    fun wholePagePictures_stayWithinTheSizeLimits() {
        val a4 = PageSize(595f, 842f)
        // At an ordinary zoom the picture is drawn at that zoom.
        assertEquals(1.9f, PdfPages.wholePageScale(a4, 1.9f), 0.0001f)
        // Zoomed far in, it's capped (the rest is drawn as a sharp part of the screen).
        val capped = PdfPages.wholePageScale(a4, 20f)
        assertTrue(842f * capped <= PdfPages.MAX_SIDE_PX + 0.5f)
        assertTrue(595f * capped * 842f * capped <= PdfPages.MAX_PIXELS + 1f)
        // A huge poster page is capped too.
        val poster = PageSize(3370f, 2384f)
        val s = PdfPages.wholePageScale(poster, 2f)
        assertTrue(3370f * s <= PdfPages.MAX_SIDE_PX + 0.5f)
    }

    @Test
    fun libraryFilter_separatesPdfsFromHandwrittenNotebooks() {
        fun book(id: String, pdf: Boolean) = NotebookListItem(
            Notebook(id, "f", id, PageTemplate.LINED, 595f, 842f, Instant.EPOCH, Instant.EPOCH),
            pageCount = 1, isPdf = pdf,
        )
        val contents = FolderContents(notebooks = listOf(book("a", false), book("b", true), book("c", false)), loaded = true)
        fun ids(filter: LibraryFilter) = contents.itemsFor(filter).map { (it as LibraryItem.Book).item.notebook.id }
        assertEquals(listOf("a", "b", "c"), ids(LibraryFilter.ALL))
        assertEquals(listOf("b"), ids(LibraryFilter.PDFS))
        assertEquals(listOf("a", "c"), ids(LibraryFilter.NOTEBOOKS))
        assertEquals(emptyList<String>(), ids(LibraryFilter.FOLDERS))
        assertEquals(listOf("all", "pdfs", "notebooks", "folders"), LibraryFilter.entries.map { it.key })
    }
}
