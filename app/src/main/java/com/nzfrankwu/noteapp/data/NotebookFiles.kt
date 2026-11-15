package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.domain.ink.InkStroke
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
    data class Ok(val strokes: List<InkStroke>) : PageRead
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
            PageRead.Ok(PageInkCodec.decode(file.readBytes()))
        } catch (e: PageFileException) {
            PageRead.Damaged(e.message ?: "Damaged page file")
        } catch (e: java.io.IOException) {
            PageRead.Damaged("Can't read the page file: ${e.message}")
        }
    }

    /** Saves the page's strokes, replacing the old file only once the new one is safely written. */
    fun write(notebookId: String, pageId: String, strokes: List<InkStroke>) {
        val target = pageFile(notebookId, pageId)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
        FileOutputStream(temp).use { out ->
            out.write(PageInkCodec.encode(strokes))
            out.flush()
            out.fd.sync() // on the disk, not just in memory
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
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
