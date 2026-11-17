package com.nzfrankwu.noteapp.data.backup.drive

import com.nzfrankwu.noteapp.data.NotebookFiles
import com.nzfrankwu.noteapp.data.PdfExport
import com.nzfrankwu.noteapp.data.backup.Md5
import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.db.Page
import com.nzfrankwu.noteapp.ui.notebook.InkBrushes
import kotlinx.coroutines.flow.first
import java.io.File

/** The app's notebooks for [PdfCopies]: from the database and the saved page files. */
class AppPdfSource(private val db: NoteAppDatabase, private val files: NotebookFiles) : PdfCopies.Source {
    override suspend fun wanted(): List<PdfCopies.Wanted> {
        val folders = db.folderDao().getAll().associateBy { it.id }
        return db.notebookDao().observeAllActive().first().map { nb ->
            val pages = db.notebookDao().getPages(nb.id)
            PdfCopies.Wanted(nb.id, nb.title, pathOf(nb.folderId, folders), version(nb, pages))
        }
    }

    override suspend fun allIds(): Set<String> = db.notebookDao().getAllIds().toSet()

    override suspend fun export(notebookId: String, out: File) {
        val nb = db.notebookDao().getNotebook(notebookId) ?: error("Notebook gone")
        PdfExport(files).export(nb, db.notebookDao().getPages(notebookId), out, InkBrushes::exportShape)
    }

    /**
     * Changes when anything the PDF shows changes: the paper, the pages (order, size, edits) and
     * their saved files. Not the title or folder (those only rename or move the copy).
     */
    private fun version(nb: Notebook, pages: List<Page>): String = Md5.of(
        buildString {
            append(nb.template).append('|').append(nb.pageWidth).append('x').append(nb.pageHeight)
            for (p in pages) {
                append('\n').append(p)
                val file = files.pageFile(nb.id, p.id)
                append('|').append(file.length()).append('|').append(file.lastModified())
            }
        }.toByteArray(),
    )

    companion object {
        /** Folder names from the top down to [folderId] (empty if the notebook has no folder). */
        fun pathOf(folderId: String?, folders: Map<String, Folder>): List<String> {
            val path = ArrayList<String>()
            var id = folderId
            while (id != null && path.size < 32) { // 32: a guard against a loop in damaged data
                val folder = folders[id] ?: break
                path.add(0, folder.name)
                id = folder.parentId
            }
            return path
        }
    }
}
