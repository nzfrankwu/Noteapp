package com.nzfrankwu.noteapp.domain

import java.net.URI

/** What "Share → Noteapp" fills into a new task: the link and a suggested title (may be blank). */
data class SharedLinkDraft(val url: String?, val title: String)

/** Reading links shared into Noteapp from other apps (IDEA.md §2.3 "Quick-add from a link"). */
object SharedLink {
    private val urlPattern = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val canvasCoursePath = Regex("""^/courses/(\d+)(/.*)?$""")
    private const val MAX_TITLE = 200

    /**
     * [text] is what the other app shared (Android's EXTRA_TEXT), [subject] its title if it sent
     * one (EXTRA_SUBJECT). Browsers usually send the link as text and the page title as subject;
     * other apps send "Some title https://…" as one text.
     */
    fun parse(text: String?, subject: String?): SharedLinkDraft {
        val body = text?.trim().orEmpty()
        val url = urlPattern.find(body)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':', '\'')
        val leftover = (if (url != null) body.replace(url, " ") else body)
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('-', '–', '—', ':', '|', '·')
            .trim()
        val title = subject?.trim()?.takeIf { it.isNotEmpty() } ?: leftover
        return SharedLinkDraft(url = url, title = title.take(MAX_TITLE))
    }

    /**
     * The Canvas course ID in a link like https://canvas.example.edu/courses/555/assignments/3,
     * if the link is on the user's Canvas site [canvasHost]. Used to guess the task's course.
     */
    fun canvasCourseId(url: String?, canvasHost: String?): Long? {
        if (url == null || canvasHost.isNullOrBlank()) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (!uri.host.orEmpty().equals(canvasHost.trim(), ignoreCase = true)) return null
        return canvasCoursePath.find(uri.path.orEmpty())?.groupValues?.get(1)?.toLongOrNull()
    }
}
