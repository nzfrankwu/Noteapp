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
import kotlinx.coroutines.flow.MutableStateFlow
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

/** One task: live details, status changes and delete. */
class TaskDetailViewModel(
    private val taskId: String,
    private val tasks: TaskRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

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
