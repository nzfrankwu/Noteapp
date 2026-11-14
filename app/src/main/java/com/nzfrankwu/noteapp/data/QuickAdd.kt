package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.SharedLink
import java.net.URI

/** What a shared link fills into the New task form. */
data class QuickAddPrefill(val title: String, val url: String?, val courseId: String?)

/**
 * Quick-add from a link (IDEA.md §2.3, F8): turns text shared from another app into a
 * prefilled New task. If the link is a page in one of your Canvas courses, that course is
 * picked too. Nothing is saved until you tap Save.
 */
class QuickAdd(
    private val db: NoteAppDatabase,
    /** The Canvas address from Settings (e.g. "https://canvas.example.edu"), if set. */
    private val canvasAddress: suspend () -> String?,
) {
    suspend fun prefill(text: String?, subject: String?): QuickAddPrefill {
        val draft = SharedLink.parse(text, subject)
        val canvasHost = canvasAddress()?.let { runCatching { URI(it.trim()).host }.getOrNull() }
        val canvasCourseId = SharedLink.canvasCourseId(draft.url, canvasHost)
        val course = canvasCourseId?.let { db.courseDao().getByCanvasId(it) }
        return QuickAddPrefill(title = draft.title, url = draft.url, courseId = course?.id)
    }
}
