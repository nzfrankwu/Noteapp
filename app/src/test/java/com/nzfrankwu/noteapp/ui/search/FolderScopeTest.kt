package com.nzfrankwu.noteapp.ui.search

import com.nzfrankwu.noteapp.data.SearchIndex
import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.domain.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** A course folder's search covers it, the folders inside it, their notebooks and the course. */
class FolderScopeTest {
    private val t0 = Instant.parse("2026-11-18T00:00:00Z")
    private fun folder(id: String, parent: String? = null, course: String? = null) = Folder(id, id, course, t0, t0, parentId = parent)
    private fun nb(id: String, folder: String) = NotebookListItem(Notebook(id, folder, id, PageTemplate.LINED, 595f, 842f, t0, t0, null), 1)

    private val folders = listOf(
        folder("ee", course = "c-ee"), folder("tut", parent = "ee"), folder("deep", parent = "tut"),
        folder("maths", course = "c-maths"),
    )
    private val notebooks = listOf(nb("lec", "ee"), nb("t1", "deep"), nb("m1", "maths"))

    @Test
    fun aFolder_coversItsSubfolders_theirNotebooks_andItsCourse() {
        val s = FolderScope.of("ee", folders, notebooks)!!
        assertEquals(setOf("ee", "tut", "deep"), s.folderIds)
        assertEquals(setOf("lec", "t1"), s.notebookIds)
        assertEquals("c-ee", s.courseId)
    }

    @Test
    fun aSubfolder_usesItsTopFoldersCourse_andOnlyWhatsInsideIt() {
        val s = FolderScope.of("tut", folders, notebooks)!!
        assertEquals(setOf("tut", "deep"), s.folderIds)
        assertEquals(setOf("t1"), s.notebookIds)
        assertEquals("c-ee", s.courseId)
        assertNull(FolderScope.of("gone", folders, notebooks))
    }

    @Test
    fun filter_keepsOnlyThisFoldersThings_notTheFolderItself() {
        val s = FolderScope.of("ee", folders, notebooks)!!
        val all = SearchIndex.Results(
            notebooks = notebooks.map { SearchIndex.NotebookHit(it, null) },
            folders = folders.map { SearchIndex.FolderHit(it, null, null) },
        )
        val r = s.filter(all)
        assertEquals(listOf("lec", "t1"), r.notebooks.map { it.item.notebook.id })
        assertEquals(listOf("tut", "deep"), r.folders.map { it.folder.id })
    }
}
