package com.nzfrankwu.noteapp.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.TaskError
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.TaskResult
import com.nzfrankwu.noteapp.data.db.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The task form screen's state. */
data class TaskEditorState(
    val form: TaskForm,
    /** False while an existing task is still loading. */
    val loaded: Boolean,
    val error: String? = null,
    /** Set once saved: the screen closes itself. Holds the saved task's ID. */
    val savedTaskId: String? = null,
    /** The task being edited no longer exists (deleted elsewhere). */
    val missing: Boolean = false,
)

/** New task / Edit task. Survives rotation, so nothing typed is lost. */
class TaskEditorViewModel(
    private val taskId: String?,
    prefillCourseId: String?,
    prefillTitle: String?,
    prefillUrl: String?,
    private val tasks: TaskRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** New task only: a due date to start with (from "Add task on this day"). */
    prefillDueDate: LocalDate? = null,
) : ViewModel() {

    val isNew: Boolean = taskId == null

    private val _state = MutableStateFlow(
        TaskEditorState(form = TaskForm.new(prefillCourseId, prefillTitle, prefillUrl, prefillDueDate), loaded = taskId == null),
    )
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()

    /** Courses to pick from, A–Z (null while loading). */
    val courses: StateFlow<List<Course>?> = tasks.observeCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var saving = false

    init {
        if (taskId != null) {
            viewModelScope.launch {
                val task = tasks.getTask(taskId)
                _state.value = if (task == null) {
                    _state.value.copy(loaded = true, missing = true)
                } else {
                    _state.value.copy(form = TaskForm.from(task, zone), loaded = true)
                }
            }
        }
    }

    private fun edit(change: (TaskForm) -> TaskForm) = _state.update { it.copy(form = change(it.form), error = null) }

    fun onTitleChange(text: String) = edit { it.copy(title = text) }
    fun onCourseChange(courseId: String) = edit { it.copy(courseId = courseId) }
    fun onDueDateChange(date: LocalDate?) = edit { it.withDueDate(date) }
    fun onDueTimeChange(time: LocalTime?) = edit { it.withDueTime(time) }
    fun onTargetDateChange(date: LocalDate?) = edit { it.withTargetDate(date) }
    fun onTargetTimeChange(time: LocalTime?) = edit { it.withTargetTime(time) }
    fun onUrlChange(text: String) = edit { it.copy(url = text) }
    fun onNotesChange(text: String) = edit { it.copy(notes = text) }

    fun save() {
        val current = _state.value
        if (saving || !current.loaded || current.missing) return
        saving = true
        viewModelScope.launch {
            try {
                val draft = current.form.toDraft(zone)
                val result = if (taskId == null) tasks.createTask(draft) else tasks.updateTask(taskId, draft)
                _state.update {
                    when (result) {
                        is TaskResult.Success -> it.copy(savedTaskId = result.taskId)
                        is TaskResult.Failure -> it.copy(error = messageFor(result.error))
                    }
                }
            } finally {
                saving = false
            }
        }
    }

    private fun messageFor(error: TaskError): String = when (error) {
        TaskError.BlankTitle -> "Give the task a name."
        TaskError.NoCourse -> "Pick a course. Every task belongs to one."
        TaskError.NotFound -> "This task no longer exists."
        TaskError.CanvasTaskCantBeDeleted -> "Canvas assignments can't be deleted."
    }
}
