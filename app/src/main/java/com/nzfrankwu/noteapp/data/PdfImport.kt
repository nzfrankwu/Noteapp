package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.domain.PageTemplate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** How importing a PDF went. */
sealed interface PdfImportResult {
    data class Success(val notebookId: String) : PdfImportResult
    data class Failure(val message: String) : PdfImportResult
}

/**
 * Imports a PDF as a new notebook (PHASE-3.md 3.3): one page per PDF page, to write on.
 *
 * The order keeps notes safe (IDEA.md §5a):
 *  1. pick the new notebook's ID,
 *  2. copy the PDF in whole into that notebook's folder (atomically: see NotebookFiles),
 *  3. read its pages; a password-protected or broken file stops here, and the unused copy goes,
 *  4. create the notebook and its pages in one database transaction.
 * So a notebook never points at a PDF that isn't there, and an interrupted import leaves at
 * most an unused folder.
 */
class PdfImport(
    private val notebooks: NotebookRepository,
    private val files: NotebookFiles,
    private val thumbnails: Thumbnails? = null,
    private val readInfo: (File) -> PdfInfo = PdfPages::readInfo,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Imports the PDF that [open] reads into a new notebook in [folderId], titled [title].
     * [open] returns null if the file can't be opened.
     */
    suspend fun import(folderId: String, title: String, open: () -> InputStream?): PdfImportResult = withContext(io) {
        val notebookId = notebooks.newNotebookId()
        val pdfName = try {
            open()?.use { files.importPdf(notebookId, it) }
        } catch (e: java.io.IOException) {
            null
        } catch (e: SecurityException) {
            null // the other app took back its permission to read the file
        }
        if (pdfName == null) {
            files.deleteUnusedNotebookFolder(notebookId)
            return@withContext PdfImportResult.Failure(CANT_READ)
        }
        val pdf = files.assetFile(notebookId, pdfName)
        val sizes = when (val info = readInfo(pdf)) {
            is PdfInfo.Ok -> info.pageSizes
            PdfInfo.PasswordProtected -> {
                files.deleteUnusedNotebookFolder(notebookId)
                return@withContext PdfImportResult.Failure(PASSWORD)
            }
            PdfInfo.Broken -> {
                files.deleteUnusedNotebookFolder(notebookId)
                return@withContext PdfImportResult.Failure(BROKEN)
            }
        }
        when (val result = notebooks.createPdfNotebook(notebookId, folderId, title.ifBlank { DEFAULT_TITLE }, pdfName, sizes)) {
            is NotebookResult.Success -> {
                // The tile picture: the first page (a cache; never mind if it fails).
                runCatching {
                    thumbnails?.render(notebookId, PageTemplate.DEFAULT, sizes[0].width, sizes[0].height, emptyList(), pdf, 0)
                }
                PdfImportResult.Success(notebookId)
            }
            is NotebookResult.Failure -> {
                // Nothing was saved in the database, so nothing uses the copy.
                files.deleteUnusedNotebookFolder(notebookId)
                PdfImportResult.Failure(
                    if (result.error == NotebookError.NotFound) "This folder no longer exists." else BROKEN,
                )
            }
        }
    }

    companion object {
        const val DEFAULT_TITLE = "Imported PDF"
        const val CANT_READ = "The PDF couldn't be read. Try again, or copy it to the tablet first."
        const val PASSWORD = "This PDF is password-protected. Remove the password in another app, then import it again."
        const val BROKEN = "This file couldn't be opened as a PDF. It may be damaged."

        /** A notebook title from the file's name: "Lecture 3.pdf" → "Lecture 3". */
        fun titleFrom(displayName: String?): String {
            val name = displayName?.trim().orEmpty()
            val bare = if (name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4).trim() else name
            return bare.ifEmpty { DEFAULT_TITLE }.take(MAX_TITLE)
        }

        private const val MAX_TITLE = 120
    }
}
