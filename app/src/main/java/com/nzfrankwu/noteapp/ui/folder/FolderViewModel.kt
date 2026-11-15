package com.nzfrankwu.noteapp.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.FolderError
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.FolderListItem
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.TaskGrouping
import com.nzfrankwu.noteapp.domain.TaskSection
import com.nzfrankwu.noteapp.domain.TaskStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import com.nzfrankwu.noteapp.data.db.Course
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nzfrankwu.noteapp.ui.message
import com.nzfrankwu.noteapp.ui.minuteTicker
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** A folder's tasks in sections, and the time they were sorted at (for Overdue badges). */
data class FolderTasks(val sections: List<TaskSection<Task>> = emptyList(), val now: Instant = Instant.EPOCH)

/** Which dialog is open on the folder screen. */
sealed interface FolderScreenDialog {
    data class Rename(val name: String, val error: String? = null) : FolderScreenDialog
    data class LinkCourse(
        val code: String = "",
        val error: String? = null,
        /** True after "Other course code…": the code box is shown. */
        val typingOtherCode: Boolean = false,
    ) : FolderScreenDialog
    data object ConfirmUnlink : FolderScreenDialog
    data class ConfirmDelete(val error: String? = null) : FolderScreenDialog
}

/** One folder: its details, its course's tasks (grouped), and the dialogs. */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModel(
    private val folderId: String,
    private val folders: FolderRepository,
    private val tasks: TaskRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** The folder (null while loading or once deleted). */
    val folder: StateFlow<FolderListItem?> = folders.observeFolder(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The linked course's tasks in sections (Overdue → … → No due date → Finished), with the time
     * they were grouped at (refreshed every minute). Empty if no course.
     */
    val tasksNow: StateFlow<FolderTasks> = folder
        .flatMapLatest { item ->
            val courseId = item?.folder?.courseId ?: return@flatMapLatest flowOf(FolderTasks())
            combine(tasks.observeForCourse(courseId), minuteTicker(clock)) { list, now ->
                FolderTasks(TaskGrouping.sections(list, now, zone, includeFinished = true), now)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderTasks())

    private val _dialog = MutableStateFlow<FolderScreenDialog?>(null)
    val dialog: StateFlow<FolderScreenDialog?> = _dialog.asStateFlow()

    /** Set once the folder was deleted, so the screen can close itself. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun zone() = zone

    fun setStatus(taskId: String, status: TaskStatus) {
        viewModelScope.launch { tasks.setStatus(taskId, status) }
    }

    fun startRename() { _dialog.value = FolderScreenDialog.Rename(folder.value?.folder?.name.orEmpty()) }
    fun startLinkCourse() { _dialog.value = FolderScreenDialog.LinkCourse() }
    fun startUnlink() { _dialog.value = FolderScreenDialog.ConfirmUnlink }
    fun startDelete() { _dialog.value = FolderScreenDialog.ConfirmDelete() }

    /** Archive (or bring back) this folder's course. Its tasks stay here either way. */
    fun setArchived(archived: Boolean) {
        val courseId = folder.value?.folder?.courseId ?: return
        viewModelScope.launch { folders.setCourseArchived(courseId, archived) }
    }
    fun dismissDialog() { _dialog.value = null }

    /** Current courses without a folder: the choices in Link a course. */
    val coursesWithoutFolder: StateFlow<List<Course>> = folders.observeCoursesWithoutFolder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pickCourse(course: Course?) = _dialog.update {
        if (it is FolderScreenDialog.LinkCourse) it.copy(code = course?.code.orEmpty(), typingOtherCode = false, error = null) else it
    }

    fun typeOtherCourse() = _dialog.update {
        if (it is FolderScreenDialog.LinkCourse) it.copy(code = "", typingOtherCode = true, error = null) else it
    }

    fun onTextChange(text: String) = _dialog.update {
        when (it) {
            is FolderScreenDialog.Rename -> it.copy(name = text, error = null)
            is FolderScreenDialog.LinkCourse -> it.copy(code = text, error = null)
            else -> it
        }
    }

    fun confirmDialog() {
        val dialog = _dialog.value ?: return
        viewModelScope.launch {
            when (dialog) {
                is FolderScreenDialog.Rename ->
                    finish(folders.renameFolder(folderId, dialog.name)) { dialog.copy(error = it.message()) }
                is FolderScreenDialog.LinkCourse ->
                    finish(folders.linkCourse(folderId, dialog.code)) { dialog.copy(error = it.message()) }
                FolderScreenDialog.ConfirmUnlink -> {
                    folders.unlinkCourse(folderId)
                    _dialog.value = null
                }
                is FolderScreenDialog.ConfirmDelete -> when (val result = folders.deleteFolder(folderId)) {
                    is FolderResult.Success -> {
                        _dialog.value = null
                        _deleted.value = true
                    }
                    is FolderResult.Failure -> _dialog.value = dialog.copy(error = result.error.message())
                }
            }
        }
    }

    private fun finish(result: FolderResult, showError: (FolderError) -> FolderScreenDialog) {
        _dialog.value = when (result) {
            is FolderResult.Success -> null
            is FolderResult.Failure -> showError(result.error)
        }
    }
}
