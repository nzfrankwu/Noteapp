package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.domain.ink.InkImage
import com.nzfrankwu.noteapp.domain.ink.InkStroke
import com.nzfrankwu.noteapp.domain.ink.InkText
import com.nzfrankwu.noteapp.domain.ink.PageInkCodec
import com.nzfrankwu.noteapp.domain.ink.PageFileException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** What reading a page's file found. */
sealed interface PageRead {
    /** No file yet: the page has never been written on. */
    data object Missing : PageRead
    data class Ok(
        val strokes: List<InkStroke>,
        val images: List<InkImage> = emptyList(),
        val texts: List<InkText> = emptyList(),
    ) : PageRead
    /** The file exists but can't be read. It's left untouched; [reason] says why. */
    data class Damaged(val reason: String) : PageRead
}

/**
 * The handwriting files: one file per page, `<root>/notebooks/<notebookId>/<pageId>.ink`
 * (IDEA.md §5a: storage designed for backup; Phase 6 uploads only the pages that changed).
 * [root] is the app's private files folder (never the cache, which Android may clear).
 *
 * Writes are atomic: the new page goes to a temporary file, is flushed to the disk, then renamed
 * over the old one. A crash or power loss mid-save leaves either the old page or the new page,
 * never half of one.
 */
class NotebookFiles(private val root: File) {
    private fun notebookDir(notebookId: String) = File(File(root, DIR), safeName(notebookId))

    fun pageFile(notebookId: String, pageId: String): File = File(notebookDir(notebookId), "${safeName(pageId)}$EXTENSION")

    fun read(notebookId: String, pageId: String): PageRead {
        val file = pageFile(notebookId, pageId)
        if (!file.exists()) return PageRead.Missing
        return try {
            PageInkCodec.decodePage(file.readBytes()).let { PageRead.Ok(it.strokes, it.images, it.texts) }
        } catch (e: PageFileException) {
            PageRead.Damaged(e.message ?: "Damaged page file")
        } catch (e: java.io.IOException) {
            PageRead.Damaged("Can't read the page file: ${e.message}")
        }
    }

    /**
     * Saves the page's strokes, pictures and text boxes, replacing the old file only once the new
     * one is safely written. A page without pictures or text is saved in the version 1 format, as
     * before; one without text boxes in version 2.
     */
    fun write(
        notebookId: String,
        pageId: String,
        strokes: List<InkStroke>,
        images: List<InkImage> = emptyList(),
        texts: List<InkText> = emptyList(),
    ) {
        val target = pageFile(notebookId, pageId)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
        FileOutputStream(temp).use { out ->
            out.write(PageInkCodec.encode(strokes, images, texts))
            out.flush()
            out.fd.sync() // on the disk, not just in memory
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * A file kept in the notebook's folder, like an imported PDF (`pdf-<id>.pdf`) or, in an
     * `images/` subfolder, an inserted image. [name] comes from our own database.
     */
    fun assetFile(notebookId: String, name: String): File {
        require(name.isNotEmpty() && name.split('/').all { part -> part.isNotEmpty() && part != "." && part != ".." }) { "Unexpected file name: $name" }
        require(name.all { it.isLetterOrDigit() || it in "-_./" }) { "Unexpected file name: $name" }
        return File(notebookDir(notebookId), name)
    }

    /**
     * Copies a PDF in whole into the notebook's folder and returns its new file name. The copy
     * is written to a temporary file, flushed to the disk and then renamed, so the file is
     * either all there or not there at all (PHASE-3.md: imported PDFs are copied, never linked).
     */
    fun importPdf(notebookId: String, input: java.io.InputStream): String {
        val name = "pdf-${java.util.UUID.randomUUID()}.pdf"
        writeAsset(notebookId, name, input)
        return name
    }

    /** Writes [input] to the asset [name] atomically (temp file, flushed, renamed). */
    fun writeAsset(notebookId: String, name: String, input: java.io.InputStream) {
        val target = assetFile(notebookId, name)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
        try {
            FileOutputStream(temp).use { out ->
                input.copyTo(out)
                out.flush()
                out.fd.sync()
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath())
            }
        } finally {
            temp.delete() // only left over if the copy failed
        }
    }

    /** A new, unused file name for an inserted picture: "images/<uuid>.<extension>". */
    fun newImageName(extension: String): String {
        require(extension == "jpg" || extension == "png") { "Unexpected picture type: $extension" }
        return "images/${java.util.UUID.randomUUID()}.$extension"
    }

    /**
     * Deletes the folder of a notebook that was never created (an import that failed before its
     * rows were saved). Only for a notebook ID with no database rows: never for a real notebook.
     */
    fun deleteUnusedNotebookFolder(notebookId: String) {
        notebookDir(notebookId).deleteRecursively()
    }

    /** Removes temporary files left by a save that was cut off (the real page files stay). */
    fun cleanUpUnfinishedSaves(notebookId: String) {
        notebookDir(notebookId).listFiles { f -> f.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
    }

    private fun safeName(id: String): String {
        require(id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Unexpected ID: $id" }
        return id
    }

    companion object {
        const val DIR = "notebooks"
        const val EXTENSION = ".ink"
        private const val TEMP_SUFFIX = ".saving"
    }
}
