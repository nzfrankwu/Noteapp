package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.Notebook
import java.io.File

/** One folder in the "Add to Noteapp" list, [depth] levels inside a top-level folder (0 = top). */
data class FolderRow(val folder: Folder, val depth: Int)

/** A folder heading and its notebooks, for "Add to a notebook". */
data class NotebookGroup(val folder: FolderRow, val notebooks: List<Notebook>)

/** What kind of thing was shared into Noteapp. */
enum class SharedKind { PDF, PICTURES }

/** A file shared into Noteapp, already copied into the inbox, with the name the other app gave it. */
data class InboxFile(val file: File, val displayName: String?)

/** A share waiting for the person to pick where it goes (PHASE-3.md 3.8). */
data class SharedFiles(val kind: SharedKind, val files: List<InboxFile>)

/** Builds the lists the "Add to Noteapp" sheet shows. Plain Kotlin. */
object ShareTargets {
    /**
     * Every folder as a tree, depth first: top-level folders A–Z, each followed by its subfolders
     * A–Z (any depth). A folder whose parent is missing is shown at the top level, so nothing is
     * ever unreachable.
     */
    fun folderTree(folders: List<Folder>): List<FolderRow> {
        val ids = folders.mapTo(HashSet()) { it.id }
        val children = folders.groupBy { f -> f.parentId?.takeIf { it in ids && it != f.id } }
        val out = ArrayList<FolderRow>(folders.size)
        val seen = HashSet<String>()
        fun add(parent: String?, depth: Int) {
            for (f in children[parent].orEmpty().sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })) {
                if (!seen.add(f.id)) continue // a loop in the data: show each folder once
                out += FolderRow(f, depth)
                add(f.id, depth + 1)
            }
        }
        add(null, 0)
        // Anything left (only possible with a parent loop) still gets listed.
        folders.filter { it.id !in seen }.sortedBy { it.name.lowercase() }.forEach { out += FolderRow(it, 0) }
        return out
    }

    /** The notebooks (not deleted) under their folders, in tree order; folders without notebooks are left out. */
    fun notebooksByFolder(tree: List<FolderRow>, notebooks: List<Notebook>): List<NotebookGroup> {
        val byFolder = notebooks.filter { it.deletedAt == null }.groupBy { it.folderId }
        return tree.mapNotNull { row ->
            byFolder[row.folder.id]?.sortedBy { it.title.lowercase() }?.let { NotebookGroup(row, it) }
        }
    }
}
