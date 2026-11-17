package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.ink.PageInkCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Duration

/**
 * Removes files nothing uses any more (PHASE-6.md 6.5): pictures taken off every page, bookmark
 * pictures of deleted bookmarks, PDFs no page shows, leftovers of saves that were cut off, and the
 * folders of notebooks that no longer exist.
 *
 * Careful by design. A file is only removed when ALL of these hold:
 * - nothing refers to it: no page file (deleted pages too), no page's PDF, no bookmark
 * - it's older than [KEEP] (so a recent Undo can still bring it back)
 * - its notebook isn't open in the editor, and isn't where the copy/paste clipboard came from
 * - every page file of the notebook could be read (one damaged page file: the notebook is skipped)
 *
 * Holds [StorageLock.backup], so it never runs during a backup or a restore.
 */
class FileCleanup(
    private val db: NoteAppDatabase,
    /** The app's files folder (holds `notebooks/`). */
    private val root: File,
    /** Notebooks to leave alone (open in the editor, or the clipboard's source). */
    private val inUse: () -> Set<String>,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** What one run removed. */
    data class Result(val files: Int = 0, val folders: Int = 0)

    suspend fun run(): Result = StorageLock.backup.withLock {
        withContext(Dispatchers.IO) { runLocked() }
    }

    private suspend fun runLocked(): Result {
        val dao = db.notebookDao()
        val now = clock.millis()
        val cutoff = now - KEEP.toMillis()
        val leftoverCutoff = now - LEFTOVER_KEEP.toMillis()
        val busy = inUse()
        val known = dao.getAllIds().toHashSet()
        var removedFiles = 0
        var removedFolders = 0
        val dirs = File(root, NotebookFiles.DIR).listFiles { f -> f.isDirectory }.orEmpty()
        for (dir in dirs) {
            val id = dir.name
            if (id in busy) continue
            val all = dir.walkTopDown().filter { it.isFile }.toList()
            if (id !in known) {
                // A notebook that no longer exists (purged, or an import that failed halfway).
                if (all.all { it.lastModified() < cutoff } && dir.deleteRecursively()) removedFolders++
                continue
            }
            for (f in all) {
                if (CleanupRules.isLeftover(f.name) && f.lastModified() < leftoverCutoff && f.delete()) removedFiles++
            }
            val referenced = referencedBy(dir) ?: continue // a page file can't be read: touch nothing
            referenced += dao.getPdfFiles(id)
            referenced += dao.getBookmarkImages(id)
            for (f in all) {
                if (!f.exists()) continue
                val name = f.relativeTo(dir).invariantSeparatorsPath
                if (CleanupRules.isUnused(name, referenced) && f.lastModified() < cutoff && f.delete()) removedFiles++
            }
        }
        return Result(removedFiles, removedFolders)
    }

    /** Every picture the notebook's page files use, or null if any page file can't be read. */
    private fun referencedBy(dir: File): MutableSet<String>? {
        val out = HashSet<String>()
        val pages = dir.listFiles { f -> f.isFile && f.name.endsWith(NotebookFiles.EXTENSION) }.orEmpty()
        for (page in pages) {
            val content = try {
                PageInkCodec.decodePage(page.readBytes())
            } catch (e: Exception) {
                return null
            }
            content.images.mapTo(out) { it.asset }
        }
        return out
    }

    companion object {
        /** How old an unused file must be before it's removed. */
        val KEEP: Duration = Duration.ofDays(30)

        /** How old a cut-off save's leftover must be before it's removed. */
        val LEFTOVER_KEEP: Duration = Duration.ofDays(1)
    }
}

/** Which files of a notebook's folder the clean-up may consider. Plain Kotlin. */
object CleanupRules {
    /** A temporary file of a save or copy that never finished. */
    fun isLeftover(fileName: String): Boolean = fileName.endsWith(".saving") || fileName.endsWith(".writing")

    /**
     * True if [name] (a path inside a notebook's folder, like `images/a.png`) is a picture, bookmark
     * picture or imported PDF that nothing in [referenced] uses. Page files and anything else are
     * never "unused".
     */
    fun isUnused(name: String, referenced: Set<String>): Boolean {
        if (isLeftover(name)) return false
        val oneLevel = name.count { it == '/' } == 1
        val kind = (name.startsWith("images/") && oneLevel) ||
            (name.startsWith("bookmarks/") && oneLevel) ||
            (name.startsWith("pdf-") && name.endsWith(".pdf") && '/' !in name)
        return kind && name !in referenced
    }
}
