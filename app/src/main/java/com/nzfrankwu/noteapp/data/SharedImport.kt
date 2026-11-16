package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.domain.PageSize
import com.nzfrankwu.noteapp.domain.ink.InkImage
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * PDFs and pictures shared into Noteapp from other apps (PHASE-3.md 3.8).
 *
 * Shared files are first copied into a private inbox ([copyToInbox]): another app's permission
 * to read them can lapse at any moment. From there:
 *  - a PDF becomes a new PDF notebook in a folder (see PdfImport);
 *  - pictures each become a page of their own, shaped like the picture, either at the end of an
 *    existing notebook or as a new notebook in a folder.
 *
 * Each picture's page file is written BEFORE its page is added, so an open notebook never sees
 * the page empty (and never saves it empty over the picture).
 */
class SharedImport(
    private val notebooks: NotebookRepository,
    private val files: NotebookFiles,
    private val images: ImageImport,
    private val pdfs: PdfImport,
) {
    /** Imports the PDF [open] reads as a new notebook in [folderId]. */
    suspend fun pdfToFolder(folderId: String, title: String, open: () -> InputStream?): PdfImportResult =
        pdfs.import(folderId, title, open)

    /** Adds each picture as a new page at the end of notebook [notebookId]. Returns how many were added. */
    suspend fun picturesToNotebook(notebookId: String, sources: List<() -> InputStream?>): Int {
        notebooks.getNotebook(notebookId) ?: return 0
        var added = 0
        for (open in sources) {
            val picture = images.import(notebookId, open) ?: continue
            val size = PageSize.forPicture(picture.widthPx, picture.heightPx)
            val pageId = notebooks.newPageId()
            files.write(notebookId, pageId, emptyList(), listOf(fill(picture, size)))
            if (notebooks.addPage(notebookId, Int.MAX_VALUE, size, pageId) is NotebookResult.Success) added++
        }
        return added
    }

    /**
     * Makes a new notebook titled [title] in [folderId], one page per picture. Returns its ID, or
     * null if no picture could be read (then nothing is created).
     */
    suspend fun picturesToNewNotebook(folderId: String, title: String, sources: List<() -> InputStream?>): String? {
        val notebookId = notebooks.newNotebookId()
        val pages = ArrayList<Pair<String, PageSize>>()
        for (open in sources) {
            val picture = images.import(notebookId, open) ?: continue
            val size = PageSize.forPicture(picture.widthPx, picture.heightPx)
            val pageId = notebooks.newPageId()
            files.write(notebookId, pageId, emptyList(), listOf(fill(picture, size)))
            pages += pageId to size
        }
        if (pages.isEmpty() || notebooks.createNotebookOfPages(notebookId, folderId, title, pages) !is NotebookResult.Success) {
            // Nothing in the database uses this folder: remove the copies.
            files.deleteUnusedNotebookFolder(notebookId)
            return null
        }
        return notebookId
    }

    private fun fill(picture: ImportedImage, size: PageSize) =
        InkImage(UUID.randomUUID().toString(), picture.asset, 0f, 0f, size.width, size.height)

    companion object {
        /** The inbox folder inside the app's private files (not the cache: it must survive until used). */
        const val INBOX_DIR = "shared-inbox"

        /**
         * Copies a shared file into [inbox] right away, as "<random>.<extension>". Returns the copy,
         * or null if it couldn't be read. Blocking: call it off the main thread.
         */
        fun copyToInbox(inbox: File, extension: String, open: () -> InputStream?): File? = try {
            inbox.mkdirs()
            val target = File(inbox, "${UUID.randomUUID()}.$extension")
            val copied = open()?.use { input -> target.outputStream().use { input.copyTo(it) }; true } ?: false
            if (copied && target.length() > 0) target else { target.delete(); null }
        } catch (e: Exception) {
            null
        }

        /** "Shared pictures 16 Nov"-style title, or the single picture's name without its extension. */
        fun titleForPictures(displayNames: List<String?>, dateText: String): String {
            val single = displayNames.singleOrNull()?.substringBeforeLast('.')?.trim()
            return if (!single.isNullOrEmpty()) single.take(120) else "Shared pictures $dateText"
        }
    }
}
