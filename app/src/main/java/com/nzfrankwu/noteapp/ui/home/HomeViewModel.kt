package com.nzfrankwu.noteapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.FolderError
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.db.FolderListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which folder dialog is open on the home screen, with what's typed in it. */
sealed interface FolderDialog {
    data class New(val name: String = "", val courseCode: String = "", val error: String? = null) : FolderDialog
    data class Rename(val folderId: String, val name: String, val error: String? = null) : FolderDialog
    data class ConfirmDelete(val folderId: String, val name: String, val courseCode: String?) : FolderDialog
}

/**
 * The home screen's state: the folder list (live from the database) and the open dialog.
 * Survives rotation, so a half-typed dialog isn't lost.
 */
class HomeViewModel(private val folderRepository: FolderRepository) : ViewModel() {

    /** All folders A–Z, or null while they're still loading. */
    val folders: StateFlow<List<FolderListItem>?> = folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    private val _dialog = MutableStateFlow<FolderDialog?>(null)
    val dialog: StateFlow<FolderDialog?> = _dialog.asStateFlow()

    private var saving = false

    fun startNewFolder() {
        _dialog.value = FolderDialog.New()
    }

    fun startRename(item: FolderListItem) {
        _dialog.value = FolderDialog.Rename(item.folder.id, item.folder.name)
    }

    fun startDelete(item: FolderListItem) {
        _dialog.value = FolderDialog.ConfirmDelete(item.folder.id, item.folder.name, item.courseCode)
    }

    fun dismissDialog() {
        _dialog.value = null
    }

    fun onNameChange(name: String) = _dialog.update {
        when (it) {
            is FolderDialog.New -> it.copy(name = name, error = null)
            is FolderDialog.Rename -> it.copy(name = name, error = null)
            else -> it
        }
    }

    fun onCourseCodeChange(code: String) = _dialog.update {
        if (it is FolderDialog.New) it.copy(courseCode = code, error = null) else it
    }

    /** The dialog's main button: Create, Save or Delete. */
    fun confirmDialog() {
        val dialog = _dialog.value ?: return
        if (saving) return // ignore a quick double tap
        saving = true
        viewModelScope.launch {
            try {
                when (dialog) {
                    is FolderDialog.New -> {
                        val result = folderRepository.createFolder(dialog.name, dialog.courseCode)
                        finish(result) { error -> dialog.copy(error = messageFor(error)) }
                    }
                    is FolderDialog.Rename -> {
                        val result = folderRepository.renameFolder(dialog.folderId, dialog.name)
                        finish(result) { error -> dialog.copy(error = messageFor(error)) }
                    }
                    is FolderDialog.ConfirmDelete -> {
                        folderRepository.deleteFolder(dialog.folderId)
                        _dialog.value = null
                    }
                }
            } finally {
                saving = false
            }
        }
    }

    private fun finish(result: FolderResult, showError: (FolderError) -> FolderDialog) {
        _dialog.value = when (result) {
            is FolderResult.Success -> null
            is FolderResult.Failure -> showError(result.error)
        }
    }

    private fun messageFor(error: FolderError): String = when (error) {
        FolderError.BlankName -> "Give the folder a name."
        FolderError.NotFound -> "This folder no longer exists."
        is FolderError.CourseAlreadyLinked ->
            "${error.courseCode} is already linked to the folder “${error.folderName}”."
    }
}
