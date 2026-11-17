package com.nzfrankwu.noteapp.data.search

import com.nzfrankwu.noteapp.data.NotebookFiles
import com.nzfrankwu.noteapp.data.PageRead
import com.nzfrankwu.noteapp.data.PdfText
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.db.Page
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps [NoteTextStore] in step with the notes (PHASE-7.md 7.9). Runs in the background a few
 * seconds after start-up and after changes; reads only pages whose source changed:
 * - a PDF page's words, keyed by its PDF file, page number and the file's size (imported PDFs
 *   are written once, never changed)
 * - a page's text boxes, keyed by its page file's size and time
 *
 * Pages and notebooks that are gone (or deleted) leave the store. A page or PDF that can't be
 * read is skipped and tried again next time. Nothing here changes the notes.
 */
class NoteTextIndexer(
    private val db: NoteAppDatabase,
    private val files: NotebookFiles,
    private val store: NoteTextStore,
) {
    private val oneAtATime = Mutex()

    data class Result(val read: Int, val removed: Int)

    suspend fun run(): Result = oneAtATime.withLock {
        withContext(Dispatchers.IO) {
            val dao = db.notebookDao()
            val notebooks = dao.getAllActiveNotebooks().associateBy { it.id }
            val pages = dao.getAllActivePages().filter { it.notebookId in notebooks }
            val known = store.keys()

            val gone = known.keys - pages.mapTo(HashSet()) { it.id }
            store.delete(gone)

            // What each page's text should be made from now.
            val wanted = pages.map { page -> page to NoteTextStore.Keys(pdfKey(page), boxKey(page)) }
            val stale = wanted.filter { (page, keys) -> known[page.id] != keys }

            var read = 0
            // PDF pages grouped by their PDF, so each file is opened once.
            val byPdf = stale.groupBy { (page, _) -> page.pdfFile?.let { page.notebookId to it } }
            for ((pdf, group) in byPdf) {
                coroutineContext.ensureActive()
                val doc = pdf?.let { (nb, file) -> runCatching { PDDocument.load(files.assetFile(nb, file)) }.getOrNull() }
                try {
                    val rows = group.mapNotNull { (page, keys) ->
                        val notebook = notebooks.getValue(page.notebookId)
                        val pdfText = when {
                            page.pdfFile == null -> null
                            doc == null -> return@mapNotNull null // can't open it now: try again next time
                            else -> pdfText(doc, page, page.size(notebook))
                        }
                        val boxText = when (val r = files.read(page.notebookId, page.id)) {
                            is PageRead.Ok -> r.texts.joinToString("\n") { it.text }.ifBlank { null }
                            is PageRead.Damaged -> return@mapNotNull null // leave it; try again later
                            else -> null // no file yet: an empty page
                        }
                        NoteTextStore.Row(page.id, page.notebookId, pdfText, boxText, keys.pdfKey, keys.boxKey)
                    }
                    store.put(rows)
                    read += rows.size
                } finally {
                    runCatching { doc?.close() }
                }
            }
            Result(read, gone.size)
        }
    }

    private fun pdfKey(page: Page): String? {
        val file = page.pdfFile ?: return null
        val length = runCatching { files.assetFile(page.notebookId, file).length() }.getOrDefault(0L)
        return "$file#${page.pdfPage}:$length"
    }

    private fun boxKey(page: Page): String {
        val f = files.pageFile(page.notebookId, page.id)
        return if (f.isFile) "${f.length()}:${f.lastModified()}" else "none"
    }

    private fun pdfText(doc: PDDocument, page: Page, size: com.nzfrankwu.noteapp.domain.PageSize): String? {
        val index = page.pdfPage ?: return null
        if (index !in 0 until doc.numberOfPages) return null
        val text = try {
            PdfText.read(doc, index, size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        if (text.isEmpty) return null
        // Words in reading order; a new line where PdfBox's line changes.
        val out = StringBuilder()
        var line = -1
        for (w in text.words) {
            if (out.isNotEmpty()) out.append(if (w.line != line) '\n' else ' ')
            out.append(w.text)
            line = w.line
        }
        return out.toString()
    }
}
