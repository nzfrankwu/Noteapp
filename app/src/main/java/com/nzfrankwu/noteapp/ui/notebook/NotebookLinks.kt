package com.nzfrankwu.noteapp.ui.notebook

import com.nzfrankwu.noteapp.data.LinkChoices
import com.nzfrankwu.noteapp.data.LinkRepository
import com.nzfrankwu.noteapp.data.LinkResult
import com.nzfrankwu.noteapp.data.TaskChoices
import com.nzfrankwu.noteapp.data.Unlinked
import com.nzfrankwu.noteapp.data.db.FolderPlace
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.PageSpot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The open notebook's links to tasks and its bookmarks (PHASE-4.md 4.3, 4.5): the chip by Back,
 * "Link to a task…", and the bookmark sheet. Lives as long as the notebook's ViewModel ([scope]).
 */
class NotebookLinks(
    private val notebookId: String,
    private val repo: LinkRepository,
    private val scope: CoroutineScope,
) {
    /** The tasks this notebook is linked to, in the order they were linked. */
    val tasks: StateFlow<List<TaskWithCourse>> = repo.observeTasksForNotebook(notebookId)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The course this notebook's folder belongs to (through its top folder), or null. */
    private val courseId: StateFlow<String?> =
        combine(
            // Only this notebook's folder matters: page saves touch the notebook, not its folder.
            repo.observeNotebook(notebookId).map { it?.folderId }.distinctUntilChanged(),
            repo.observeAllFolders(),
        ) { folderId, folders ->
            folderId?.let { FolderPlace.of(it, folders, emptyList())?.top?.courseId }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The tasks "Link to a task…" offers: this course's first, then the others. Already linked ones are left out. */
    val choices: StateFlow<TaskChoices> =
        combine(repo.observeAllTasks(), tasks, courseId) { all, linked, course ->
            LinkChoices.tasks(course, all, linked.mapTo(HashSet()) { it.task.id })
        }.stateIn(scope, SharingStarted.Eagerly, TaskChoices(emptyList(), emptyList()))

    /** Every task, this course's first (linked ones too), for the bookmark sheet's "Other tasks". */
    val allChoices: StateFlow<TaskChoices> =
        combine(repo.observeAllTasks(), courseId) { all, course -> LinkChoices.tasks(course, all, emptySet()) }
            .stateIn(scope, SharingStarted.Eagerly, TaskChoices(emptyList(), emptyList()))

    /** A short message for the snackbar after a link change, with Undo when [undo] is set. */
    data class Notice(val text: String, val undo: (() -> Unit)? = null)

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()
    fun noticeShown() { _notice.value = null }

    fun link(task: TaskWithCourse) {
        scope.launch {
            if (repo.link(task.task.id, notebookId) is LinkResult.Success) _notice.value = Notice("Linked to “${task.task.title}”")
        }
    }

    /** Unlinks the task (and removes its bookmarks in this notebook), with Undo. */
    fun unlink(task: TaskWithCourse) {
        scope.launch {
            val removed: Unlinked = repo.unlink(task.task.id, notebookId) ?: return@launch
            val extra = when (removed.bookmarks.size) {
                0 -> ""
                1 -> " and its bookmark"
                else -> " and its ${removed.bookmarks.size} bookmarks"
            }
            _notice.value = Notice("Unlinked “${task.task.title}”$extra") { scope.launch { repo.restoreUnlinked(removed) } }
        }
    }

    /** Bookmarks [pageId] (at [spot], or the whole page) for [task], linking it if needed. */
    fun addBookmark(task: TaskWithCourse, pageId: String, note: String?, spot: PageSpot?) {
        scope.launch {
            when (repo.addBookmark(task.task.id, pageId, note, spot)) {
                is LinkResult.Success -> _notice.value = Notice("Bookmarked for “${task.task.title}”")
                is LinkResult.Failure -> _notice.value = Notice("The bookmark couldn't be saved: the page or task no longer exists.")
            }
        }
    }
}
