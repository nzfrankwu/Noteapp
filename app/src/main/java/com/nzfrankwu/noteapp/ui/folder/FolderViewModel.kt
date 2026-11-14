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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.ZoneId

/** Which dialog is open on the folder screen. */
sealed interface FolderScreenDialog {
    data class Rename(val name: String, val error: String? = null) : FolderScreenDialog
    data class LinkCourse(val code: String = "", val error: String? = null) : FolderScreenDialog
    data object ConfirmUnlink : FolderScreenDialog
    data object ConfirmDelete : FolderScreenDialog
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

    /** The linked course's tasks in sections (Overdue → … → No due date → Finished). Empty if no course. */
    val sections: StateFlow<List<TaskSection<Task>>> = folder
        .flatMapLatest { item ->
            val courseId = item?.folder?.courseId ?: return@flatMapLatest flowOf(emptyList())
            tasks.observeForCourse(courseId).map { TaskGrouping.sections(it, clock.instant(), zone, includeFinished = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dialog = MutableStateFlow<FolderScreenDialog?>(null)
    val dialog: StateFlow<FolderScreenDialog?> = _dialog.asStateFlow()

    /** Set once the folder was deleted, so the screen can close itself. */
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    fun now() = clock.instant()
    fun zone() = zone

    fun setStatus(taskId: String, status: TaskStatus) {
        viewModelScope.launch { tasks.setStatus(taskId, status) }
    }

    fun startRename() { _dialog.value = FolderScreenDialog.Rename(folder.value?.folder?.name.orEmpty()) }
    fun startLinkCourse() { _dialog.value = FolderScreenDialog.LinkCourse() }
    fun startUnlink() { _dialog.value = FolderScreenDialog.ConfirmUnlink }
    fun startDelete() { _dialog.value = FolderScreenDialog.ConfirmDelete }
    fun dismissDialog() { _dialog.value = null }

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
                    finish(folders.renameFolder(folderId, dialog.name)) { dialog.copy(error = messageFor(it)) }
                is FolderScreenDialog.LinkCourse ->
                    finish(folders.linkCourse(folderId, dialog.code)) { dialog.copy(error = messageFor(it)) }
                FolderScreenDialog.ConfirmUnlink -> {
                    folders.unlinkCourse(folderId)
                    _dialog.value = null
                }
                FolderScreenDialog.ConfirmDelete -> {
                    folders.deleteFolder(folderId)
                    _dialog.value = null
                    _deleted.value = true
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

    private fun messageFor(error: FolderError): String = when (error) {
        FolderError.BlankName -> "Give the folder a name."
        FolderError.BlankCourseCode -> "Type a course code, e.g. ELECTENG 204."
        FolderError.NotFound -> "This folder no longer exists."
        is FolderError.CourseAlreadyLinked -> "${error.courseCode} is already linked to the folder “${error.folderName}”."
    }
}
