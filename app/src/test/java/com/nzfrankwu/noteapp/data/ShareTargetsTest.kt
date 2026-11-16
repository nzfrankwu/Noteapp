package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.domain.PageTemplate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The lists in "Add to Noteapp" (PHASE-3.md 3.8). */
class ShareTargetsTest {
    private fun folder(id: String, name: String, parent: String? = null) =
        Folder(id = id, name = name, courseId = null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, parentId = parent)

    private fun notebook(id: String, title: String, folderId: String?, deleted: Boolean = false) =
        Notebook(id, folderId, title, PageTemplate.LINED, 595f, 842f, Instant.EPOCH, Instant.EPOCH, if (deleted) Instant.EPOCH else null)

    private val folders = listOf(
        folder("e", "ELECTENG 204"),
        folder("t", "Tutorials", parent = "e"),
        folder("l", "lectures", parent = "e"),
        folder("w", "Week 1", parent = "l"),
        folder("c", "COMPSYS 201"),
        folder("o", "Orphan", parent = "gone"),
    )

    @Test
    fun folders_formATree_AtoZ_withSubfoldersUnderTheirParent() {
        val tree = ShareTargets.folderTree(folders)
        assertEquals(listOf("c", "e", "l", "w", "t", "o"), tree.map { it.folder.id })
        assertEquals(listOf(0, 0, 1, 2, 1, 0), tree.map { it.depth })
    }

    @Test
    fun aLoopInTheData_stillListsEveryFolderOnce() {
        val loop = listOf(folder("a", "A", parent = "b"), folder("b", "B", parent = "a"), folder("x", "X"))
        val tree = ShareTargets.folderTree(loop)
        assertEquals(setOf("a", "b", "x"), tree.map { it.folder.id }.toSet())
        assertEquals(3, tree.size)
    }

    @Test
    fun notebooks_areGroupedUnderTheirFolders_inTreeOrder_withoutDeletedOnes() {
        val tree = ShareTargets.folderTree(folders)
        val groups = ShareTargets.notebooksByFolder(
            tree,
            listOf(notebook("1", "Lecture 2", "l"), notebook("2", "lecture 1", "l"), notebook("3", "Old", "c", deleted = true), notebook("4", "Lab", "e")),
        )
        assertEquals(listOf("e", "l"), groups.map { it.folder.folder.id })
        assertEquals(listOf("lecture 1", "Lecture 2"), groups[1].notebooks.map { it.title })
    }
}
