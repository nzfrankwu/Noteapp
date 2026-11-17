package com.nzfrankwu.noteapp.data.backup.drive

import java.io.File

/**
 * Readable PDF copies of every notebook in Google Drive (PHASE-6.md 6.12, IDEA.md §2.9):
 *
 * ```
 * My Drive/Noteapp/Notes (PDF)/<folder>/<subfolder>/<notebook title>.pdf
 * ```
 *
 * They're for reading on a computer or phone, not for restoring (the full backup does that).
 * Each copy is tagged with its notebook's id and a "version" of its content, so only notebooks
 * that changed are exported again; renamed or moved notebooks and folders are moved in Drive.
 * A notebook in Recently deleted keeps its copy until it's deleted for good.
 */
class PdfCopies(
    private val drive: DriveApi,
    private val source: Source,
    /** A scratch folder (in the cache) for the PDFs being made. */
    private val scratch: File,
) {
    /** One notebook as it should appear in Drive. */
    data class Wanted(
        val notebookId: String,
        val title: String,
        /** Folder names from the top (e.g. ["Electromagnetics", "Labs"]); empty = directly in "Notes (PDF)". */
        val folderPath: List<String>,
        /** Changes whenever the notebook's content does. */
        val version: String,
    )

    /** What the app knows (the database and page files; a fake in tests). */
    interface Source {
        /** Every notebook that isn't deleted. */
        suspend fun wanted(): List<Wanted>

        /** Every notebook still in the database, deleted ones (Recently deleted) too. */
        suspend fun allIds(): Set<String>

        /** Writes the notebook as a PDF to [out]. */
        suspend fun export(notebookId: String, out: File)
    }

    data class Result(val uploaded: Int, val moved: Int, val removed: Int, val failed: Int)

    private class Existing(val file: DriveFile, val parentId: String)

    suspend fun sync(): Result {
        val root = drive.folder(DriveBackup.ROOT_FOLDER, null)
        val pdfRoot = drive.folder(FOLDER, root.id)

        // What's there now: every folder (by path) and every copy (by notebook).
        val folders = HashMap<List<String>, String>() // path → id
        val copies = HashMap<String, MutableList<Existing>>()
        suspend fun walk(path: List<String>, id: String) {
            folders[path] = id
            for (file in drive.listFiles(id)) {
                val notebookId = file.appProperties[NOTEBOOK_ID]
                if (notebookId != null) copies.getOrPut(notebookId) { ArrayList() } += Existing(file, id)
            }
            for (sub in drive.listFolders(id)) walk(path + sub.name, sub.id)
        }
        walk(emptyList(), pdfRoot.id)

        suspend fun folderFor(path: List<String>): String {
            folders[path]?.let { return it }
            val parent = folderFor(path.dropLast(1))
            val made = drive.createFolder(path.last(), parent, mapOf(DriveBackup.KIND to PDF_FOLDER))
            folders[path] = made.id
            return made.id
        }

        var uploaded = 0
        var moved = 0
        var removed = 0
        var failed = 0
        val wanted = source.wanted()
        scratch.mkdirs()
        for (w in wanted) {
            val name = fileName(w.title)
            val parentId = folderFor(w.folderPath.map(::folderName))
            val existing = copies[w.notebookId].orEmpty()
            val keep = existing.firstOrNull()
            // A notebook should have one copy; extras (e.g. from an interrupted run) go.
            for (extra in existing.drop(1)) {
                drive.delete(extra.file.id)
                removed++
            }
            try {
                if (keep == null || keep.file.appProperties[VERSION] != w.version) {
                    val out = File(scratch, "${w.notebookId}.pdf")
                    try {
                        source.export(w.notebookId, out)
                        val props = mapOf(DriveBackup.KIND to PDF, NOTEBOOK_ID to w.notebookId, VERSION to w.version)
                        if (keep == null) {
                            drive.upload(out, name, parentId, PDF_MIME, props)
                        } else {
                            drive.replace(keep.file.id, out, PDF_MIME, name, props)
                            if (keep.parentId != parentId) drive.move(keep.file.id, null, parentId, keep.parentId)
                        }
                        uploaded++
                    } finally {
                        out.delete()
                    }
                } else if (keep.file.name != name || keep.parentId != parentId) {
                    drive.move(
                        keep.file.id,
                        name.takeIf { it != keep.file.name },
                        parentId.takeIf { it != keep.parentId },
                        keep.parentId.takeIf { parentId != it },
                    )
                    moved++
                }
            } catch (e: DriveException) {
                throw e // Drive trouble stops the run (the next one carries on)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++ // one notebook that can't be exported doesn't stop the others
            }
        }

        // Copies of notebooks deleted for good go; Recently deleted keeps its copies.
        val stillThere = source.allIds()
        for ((notebookId, list) in copies) {
            if (notebookId in stillThere) continue
            for (c in list) {
                drive.delete(c.file.id)
                removed++
            }
        }

        // Folders nothing is in any more (after renames and moves), deepest first. A folder that
        // still holds anything (a copy in Recently deleted, a file Noteapp didn't tag) isn't empty, so it stays.
        val needed = HashSet<String>()
        for (w in wanted) {
            val path = w.folderPath.map(::folderName)
            for (n in 0..path.size) folders[path.take(n)]?.let(needed::add)
        }
        for ((path, id) in folders.entries.sortedByDescending { it.key.size }) {
            if (path.isEmpty() || id in needed) continue
            if (drive.listFiles(id).isEmpty() && drive.listFolders(id).isEmpty()) {
                drive.delete(id)
                removed++
            }
        }
        return Result(uploaded, moved, removed, failed)
    }

    companion object {
        const val FOLDER = "Notes (PDF)"
        const val PDF = "pdf"
        const val PDF_FOLDER = "pdf-folder"
        const val NOTEBOOK_ID = "notebookId"
        const val VERSION = "version"
        const val PDF_MIME = "application/pdf"

        /** The copy's name: the notebook's title (Drive allows any name; only control characters go). */
        fun fileName(title: String): String = clean(title).ifEmpty { "Notebook" }.take(120) + ".pdf"

        fun folderName(name: String): String = clean(name).ifEmpty { "Folder" }.take(120)

        private fun clean(text: String) = text.filter { !it.isISOControl() }.trim()
    }
}
