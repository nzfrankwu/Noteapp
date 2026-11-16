package com.nzfrankwu.noteapp.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.DeleteTaskResult
import com.nzfrankwu.noteapp.data.TaskError
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.data.FolderRow
import com.nzfrankwu.noteapp.data.LinkChoices
import com.nzfrankwu.noteapp.data.LinkError
import com.nzfrankwu.noteapp.data.LinkRepository
import com.nzfrankwu.noteapp.data.LinkResult
import com.nzfrankwu.noteapp.data.NotebookChoices
import com.nzfrankwu.noteapp.data.ShareTargets
import com.nzfrankwu.noteapp.data.Unlinked
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.domain.BookmarkGroup
import com.nzfrankwu.noteapp.domain.PageTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

/** What the task screen shows: loading, the task, or "gone" (deleted elsewhere). */
sealed interface TaskDetailState {
    data object Loading : TaskDetailState
    data object Gone : TaskDetailState
    data class Shown(val item: TaskWithCourse) : TaskDetailState
}

/** A new notebook waiting for a folder to go in. */
data class NewNotebookRequest(val title: String, val template: PageTemplate)

/** One task: live details, status changes and delete. */
class TaskDetailViewModel(
    private val taskId: String,
    private val tasks: TaskRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Linked notebooks and bookmarks (Phase 4). Null in tests that don't need them. */
    private val links: LinkRepository? = null,
) : ViewModel() {

    /** True when this screen can show and change linked notebooks. */
    val canLink: Boolean get() = links != null

    /** The task's linked notebooks (not deleted), in the order they were linked. */
    val linkedNotebooks: StateFlow<List<NotebookListItem>> = (links?.observeNotebooksForTask(taskId) ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The notebooks "Link notebook" offers: this course's folder first, then the others. */
    val notebookChoices: StateFlow<NotebookChoices> = if (links == null) {
        MutableStateFlow(NotebookChoices(emptyList(), emptyList()))
    } else {
        combine(tasks.observeTask(taskId), links.observeAllFolders(), links.observeAllNotebooks(), linkedNotebooks) { item, folders, all, linked ->
            val courseFolder = item?.task?.courseId?.let { courseId -> folders.firstOrNull { it.courseId == courseId } }
            LinkChoices.notebooks(courseFolder?.id, folders, all, linked.mapTo(HashSet()) { it.notebook.id })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotebookChoices(emptyList(), emptyList()))
    }

    /** Every folder as a tree, for picking where a new notebook goes when the task has no course folder. */
    val folderTree: StateFlow<List<FolderRow>> = (links?.observeAllFolders()?.map { ShareTargets.folderTree(it) } ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The task's bookmarks, grouped by notebook in page order (step 4.8). */
    val bookmarks: StateFlow<List<BookmarkGroup>> = (links?.observeBookmarksForTask(taskId) ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A notebook to open now (a new notebook made for this task); the screen opens it and calls [notebookOpened]. */
    private val _openNotebook = MutableStateFlow<String?>(null)
    val openNotebook: StateFlow<String?> = _openNotebook.asStateFlow()

    /** Set when "New notebook for this task" needs a folder (the task's course has none). */
    private val _needsFolder = MutableStateFlow<NewNotebookRequest?>(null)
    val needsFolder: StateFlow<NewNotebookRequest?> = _needsFolder.asStateFlow()

    /** What was just unlinked, for the snackbar's Undo. */
    private val _unlinked = MutableStateFlow<Pair<String, Unlinked>?>(null)
    val unlinked: StateFlow<Pair<String, Unlinked>?> = _unlinked.asStateFlow()

    fun link(notebookId: String) {
        val repo = links ?: return
        viewModelScope.launch { repo.link(taskId, notebookId) }
    }

    /** Unlinks [item] (and removes this task's bookmarks in it); the snackbar offers Undo. */
    fun unlink(item: NotebookListItem) {
        val repo = links ?: return
        viewModelScope.launch {
            repo.unlink(taskId, item.notebook.id)?.let { _unlinked.value = item.notebook.title to it }
        }
    }

    fun undoUnlink(unlinked: Unlinked) {
        val repo = links ?: return
        viewModelScope.launch { repo.restoreUnlinked(unlinked) }
    }

    fun unlinkShown() { _unlinked.value = null }

    /** Makes a notebook for this task in its course folder (or [folderId]), links it and opens it. */
    fun createNotebook(title: String, template: PageTemplate, folderId: String? = null) {
        val repo = links ?: return
        viewModelScope.launch {
            when (val result = repo.createNotebookForTask(taskId, title, folderId, template)) {
                is LinkResult.Success -> {
                    _needsFolder.value = null
                    _openNotebook.value = result.id
                }
                is LinkResult.Failure -> when (result.error) {
                    LinkError.NoCourseFolder -> _needsFolder.value = NewNotebookRequest(title, template)
                    LinkError.BlankTitle -> _message.value = "Give the notebook a name."
                    else -> _message.value = "Couldn't make the notebook: the folder or task no longer exists."
                }
            }
        }
    }

    fun cancelNewNotebook() { _needsFolder.value = null }

    /** A bookmark just deleted, for the snackbar's Undo. */
    private val _deletedBookmark = MutableStateFlow<com.nzfrankwu.noteapp.data.db.TaskBookmark?>(null)
    val deletedBookmark: StateFlow<com.nzfrankwu.noteapp.data.db.TaskBookmark?> = _deletedBookmark.asStateFlow()

    fun setBookmarkNote(bookmarkId: String, note: String) {
        val repo = links ?: return
        viewModelScope.launch { repo.setNote(bookmarkId, note) }
    }

    fun deleteBookmark(bookmarkId: String) {
        val repo = links ?: return
        viewModelScope.launch { repo.deleteBookmark(bookmarkId)?.let { _deletedBookmark.value = it } }
    }

    fun undoDeleteBookmark(bookmark: com.nzfrankwu.noteapp.data.db.TaskBookmark) {
        val repo = links ?: return
        viewModelScope.launch { repo.restoreBookmark(bookmark) }
    }

    fun deletedBookmarkShown() { _deletedBookmark.value = null }

    fun notebookOpened() { _openNotebook.value = null }

    /** The name a new notebook for this task starts with. */
    fun suggestedNotebookTitle(): String = (state.value as? TaskDetailState.Shown)?.item?.task?.title.orEmpty()

    val state: StateFlow<TaskDetailState> = tasks.observeTask(taskId)
        .map { if (it == null) TaskDetailState.Gone else TaskDetailState.Shown(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailState.Loading)

    /** Set when the task was deleted from this screen; the screen closes and offers Undo. */
    private val _deleted = MutableStateFlow<DeleteTaskResult.Deleted?>(null)
    val deleted: StateFlow<DeleteTaskResult.Deleted?> = _deleted.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun now() = clock.instant()
    fun zone() = zone

    fun setStatus(status: TaskStatus) {
        viewModelScope.launch { tasks.setStatus(taskId, status) }
    }

    fun dismissMessage() { _message.value = null }

    /** Deletes straight away (no "Are you sure?"): the snackbar's Undo brings it back. */
    fun delete() {
        viewModelScope.launch {
            when (val result = tasks.deleteTask(taskId)) {
                is DeleteTaskResult.Deleted -> _deleted.value = result
                is DeleteTaskResult.Failure -> _message.value = when (result.error) {
                    TaskError.CanvasTaskCantBeDeleted ->
                        "Canvas assignments can't be deleted, because the next sync would bring them back. Mark it Done instead."
                    else -> "This task no longer exists."
                }
            }
        }
    }

    companion object {
        /** Your own tasks, and Canvas tasks that were removed from Canvas, can be deleted. */
        fun canDelete(task: Task): Boolean = task.source == TaskSource.MANUAL || task.removedFromCanvas
    }
}
