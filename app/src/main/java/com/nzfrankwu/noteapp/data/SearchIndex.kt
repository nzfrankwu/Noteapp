package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import java.time.Instant

/**
 * Search over names and tasks (PHASE-7.md 7.8, IDEA.md §2.11). Plain Kotlin, unit-tested.
 *
 * Every word of the query must appear somewhere in the item (any case, any order), like the task
 * pickers ([LinkChoices.matches]). What counts for each kind:
 * - a task: title, course code and name, description (as plain text) and your notes
 * - a notebook: title and the folders it's in
 * - a folder: its name, its course's code and name, and the folders it's in
 *
 * Items whose title has every word come first. Tasks then go unfinished before Done, soonest
 * first (no date last), archived courses' tasks last; notebooks most recently changed first;
 * folders A–Z.
 *
 * [SearchIndex] is built once when the data changes; [search] runs on every key press.
 */
class SearchIndex(
    tasks: List<TaskWithCourse>,
    notebooks: List<NotebookListItem>,
    folders: List<Folder>,
    courses: List<Course>,
) {
    data class NotebookHit(val item: NotebookListItem, val place: String?)
    data class FolderHit(val folder: Folder, val courseCode: String?, val place: String?)
    data class Results(
        val tasks: List<TaskWithCourse> = emptyList(),
        val notebooks: List<NotebookHit> = emptyList(),
        val folders: List<FolderHit> = emptyList(),
    ) {
        val isEmpty: Boolean get() = tasks.isEmpty() && notebooks.isEmpty() && folders.isEmpty()
    }

    private class Entry<T>(val value: T, val title: String, val text: String)

    private val byId = folders.associateBy { it.id }
    private val courseById = courses.associateBy { it.id }

    /** "ELECTENG 204 › tutorials": the folders from the top down to [folderId] (null if none). */
    private fun path(folderId: String?): String? {
        val names = ArrayList<String>()
        var id = folderId
        val seen = HashSet<String>()
        while (id != null && seen.add(id)) {
            val folder = byId[id] ?: break
            names += folder.name
            id = folder.parentId
        }
        return names.asReversed().joinToString(" › ").ifEmpty { null }
    }

    /** A folder's course: its own, or (for a subfolder) the top folder's. */
    private fun courseOf(folder: Folder): Course? {
        var f: Folder? = folder
        val seen = HashSet<String>()
        while (f != null && seen.add(f.id)) {
            f.courseId?.let { return courseById[it] }
            f = f.parentId?.let(byId::get)
        }
        return null
    }

    private val taskEntries = tasks.map { t ->
        val course = t.task.courseId?.let(courseById::get)
        Entry(
            t, t.task.title,
            listOf(t.task.title, t.courseCode, course?.name, plainText(t.task.descriptionHtml), t.task.notes)
                .filterNotNull().joinToString("\n"),
        )
    }

    private val notebookEntries = notebooks.map { n ->
        val place = path(n.notebook.folderId)
        Entry(NotebookHit(n, place), n.notebook.title, listOfNotNull(n.notebook.title, place).joinToString("\n"))
    }

    private val folderEntries = folders.map { f ->
        val course = courseOf(f)
        val parentPlace = path(f.parentId)
        Entry(
            FolderHit(f, course?.code, parentPlace),
            f.name,
            listOfNotNull(f.name, course?.code, course?.name, parentPlace).joinToString("\n"),
        )
    }

    fun search(query: String, now: Instant): Results {
        val words = words(query)
        if (words.isEmpty()) return Results()
        fun <T> List<Entry<T>>.hits() = filter { e -> words.all { e.text.contains(it, ignoreCase = true) } }
        fun Entry<*>.titleMiss() = if (words.all { title.contains(it, ignoreCase = true) }) 0 else 1

        val tasks = taskEntries.hits().sortedWith(
            compareBy<Entry<TaskWithCourse>> { it.titleMiss() }
                .thenBy { if (it.value.isCourseArchived) 1 else 0 }
                .thenBy { if (it.value.task.status.isFinished) 1 else 0 }
                .thenBy { it.value.task.dueAt ?: Instant.MAX }
                .thenBy { it.value.task.title.lowercase() },
        ).map { it.value }
        val notebooks = notebookEntries.hits().sortedWith(
            compareBy<Entry<NotebookHit>> { it.titleMiss() }.thenByDescending { it.value.item.notebook.updatedAt },
        ).map { it.value }
        val folders = folderEntries.hits().sortedWith(
            compareBy<Entry<FolderHit>> { it.titleMiss() }.thenBy { it.value.folder.name.lowercase() },
        ).map { it.value }
        return Results(tasks, notebooks, folders)
    }

    companion object {
        /**
         * A short piece of [text] around the first search word, on one line, with "…" where it's
         * cut (PHASE-7.md 7.9). Null if [text] is empty.
         */
        fun snippet(text: String?, words: List<String>, around: Int = 60): String? {
            val flat = text?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
            if (flat.isEmpty()) return null
            // The words together if they're there ("slide 3"), else the first word found.
            val together = flat.indexOf(words.joinToString(" "), ignoreCase = true)
            val at = together.takeIf { it >= 0 } ?: words.map { flat.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: 0
            var start = (at - around).coerceAtLeast(0)
            var end = (at + around).coerceAtMost(flat.length)
            // Don't cut words in half.
            if (start > 0) flat.indexOf(' ', start).takeIf { it in start until at }?.let { start = it + 1 }
            if (end < flat.length) flat.lastIndexOf(' ', end).takeIf { it > at }?.let { end = it }
            return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
        }

        /** True when [text] holds [words] together, in order (spaces and line breaks count alike). */
        fun hasPhrase(text: String?, words: List<String>): Boolean =
            words.size > 1 && text != null &&
                text.replace(Regex("\\s+"), " ").contains(words.joinToString(" "), ignoreCase = true)

        fun words(query: String): List<String> = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        private val TAG = Regex("<\\s*(/?)\\s*([a-zA-Z0-9]*)[^>]*>")
        private val BLOCK = setOf("p", "br", "div", "li", "ul", "ol", "tr", "td", "th", "table", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "blockquote", "pre")
        private val ENTITY = Regex("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);")

        /** Canvas's HTML description as plain words (tags dropped, common entities decoded). */
        fun plainText(html: String?): String? {
            if (html.isNullOrBlank()) return null
            // Block tags (paragraphs, lines, list items) separate words; inline ones (<b>, <a>) don't.
            val noTags = TAG.replace(html) { m -> if (m.groupValues[2].lowercase() in BLOCK) " " else "" }
            return ENTITY.replace(noTags) { m ->
                val code = m.groupValues[1]
                when {
                    code.startsWith("#x") -> code.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: " "
                    code.startsWith("#") -> code.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: " "
                    else -> when (code.lowercase()) {
                        "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                        else -> " "
                    }
                }
            }.replace(Regex("\\s+"), " ").trim()
        }
    }
}
