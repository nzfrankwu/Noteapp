package com.nzfrankwu.noteapp.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.FolderError
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.NotebookError
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.FolderListItem
import com.nzfrankwu.noteapp.data.db.FolderPlace
import com.nzfrankwu.noteapp.data.db.NotebookListItem
import com.nzfrankwu.noteapp.data.db.SubfolderListItem
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.TaskGrouping
import com.nzfrankwu.noteapp.domain.TaskSection
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.message
import com.nzfrankwu.noteapp.ui.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A folder's tasks in sections, and the time they were sorted at (for Overdue badges). */
data class FolderTasks(val sections: List<TaskSection<Task>> = emptyList(), val now: Instant = Instant.EPOCH)

/** What a folder holds: its subfolders (A–Z) and notebooks (most recently changed first). */
data class FolderContents(
    val subfolders: List<SubfolderListItem> = emptyList(),
    val notebooks: List<NotebookListItem> = emptyList(),
    /** False until the first load finishes (so "empty" isn't flashed while loading). */
    val loaded: Boolean = false,
)

/** The filter row above a folder's grid (IDEA.md §2.1). "Notebooks" means the handwritten ones. */
enum class LibraryFilter(val label: String, val key: String) {
    ALL("All", "all"),
    PDFS("PDFs", "pdfs"),
    NOTEBOOKS("Notebooks", "notebooks"),
    FOLDERS("Folders", "folders"),
}

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

    // ---- Things inside the folder (Phase 2) ----
    data class NewSubfolder(val name: String = "", val error: String? = null) : FolderScreenDialog
    data class NewNotebook(
        val title: String,
        val template: PageTemplate = PageTemplate.DEFAULT,
        val error: String? = null,
    ) : FolderScreenDialog
    data class RenameSubfolder(val folderId: String, val name: String, val error: String? = null) : FolderScreenDialog
    data class RenameNotebook(val notebookId: String, val title: String, val error: String? = null) : FolderScreenDialog
    data class DeleteSubfolder(val folderId: String, val name: String, val error: String? = null) : FolderScreenDialog
}

/** A notebook that was just deleted, for the "Deleted … Undo" message. */
data class DeletedNotebook(val notebookId: String, val title: String)

/** One folder: its details, its subfolders and notebooks, its course's tasks, and the dialogs. */
@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModel(
    private val folderId: String,
    private val folders: FolderRepository,
    private val tasks: TaskRepository,
    private val notebooks: NotebookRepository,
    private val pdfImport: com.nzfrankwu.noteapp.data.PdfImport? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** The folder (null while loading or once deleted). */
    val folder: StateFlow<FolderListItem?> = folders.observeFolder(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Where the folder sits (its path from the top folder) and the course it belongs to. */
    val place: StateFlow<FolderPlace?> = folders.observePlace(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Its subfolders and notebooks, updating live. */
    val contents: StateFlow<FolderContents> =
        combine(folders.observeSubfolders(folderId), notebooks.observeInFolder(folderId)) { subs, books ->
            FolderContents(subs, books, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderContents())

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()
    fun setFilter(filter: LibraryFilter) { _filter.value = filter }

    /**
     * The linked course's tasks in sections (Overdue → … → No due date → Finished), with the time
     * they were grouped at (refreshed every minute). Empty if no course, and for subfolders (the
     * course's tasks show only in its top folder).
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

    /** The notebook just deleted (shown with Undo), or null. */
    private val _deletedNotebook = MutableStateFlow<DeletedNotebook?>(null)
    val deletedNotebook: StateFlow<DeletedNotebook?> = _deletedNotebook.asStateFlow()

    /** Notebooks to open right away (a newly created one). */
    private val openNotebookEvents = Channel<String>(Channel.BUFFERED)
    val openNotebook: Flow<String> = openNotebookEvents.receiveAsFlow()

    /** Current courses without a folder: the choices in Link a course. */
    val coursesWithoutFolder: StateFlow<List<Course>> = folders.observeCoursesWithoutFolder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var saving = false

    fun zone() = zone

    /** True while a PDF is being copied in (the screen shows a progress note). */
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /** A message to show once (e.g. why a PDF couldn't be imported). */
    private val messageEvents = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = messageEvents.receiveAsFlow()

    /**
     * Imports a PDF as a new notebook here and opens it (PHASE-3.md 3.3). [displayName] is the
     * file's name (for the title); [open] reads the file.
     */
    fun importPdf(displayName: String?, open: () -> java.io.InputStream?) {
        val importer = pdfImport ?: return
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                when (val result = importer.import(folderId, com.nzfrankwu.noteapp.data.PdfImport.titleFrom(displayName), open)) {
                    is com.nzfrankwu.noteapp.data.PdfImportResult.Success -> openNotebookEvents.send(result.notebookId)
                    is com.nzfrankwu.noteapp.data.PdfImportResult.Failure -> messageEvents.send(result.message)
                }
            } finally {
                _importing.value = false
            }
        }
    }

    fun setStatus(taskId: String, status: TaskStatus) {
        viewModelScope.launch { tasks.setStatus(taskId, status) }
    }

    fun startRename() { _dialog.value = FolderScreenDialog.Rename(folder.value?.folder?.name.orEmpty()) }
    fun startLinkCourse() { _dialog.value = FolderScreenDialog.LinkCourse() }
    fun startUnlink() { _dialog.value = FolderScreenDialog.ConfirmUnlink }
    fun startDelete() { _dialog.value = FolderScreenDialog.ConfirmDelete() }

    fun startNewSubfolder() { _dialog.value = FolderScreenDialog.NewSubfolder() }

    /** New notebook, with a title like "Notes 15 Nov" filled in (easy to keep or replace). */
    fun startNewNotebook() {
        val date = clock.instant().atZone(zone).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        _dialog.value = FolderScreenDialog.NewNotebook(title = "Notes $date")
    }

    fun startRenameSubfolder(item: SubfolderListItem) {
        _dialog.value = FolderScreenDialog.RenameSubfolder(item.folder.id, item.folder.name)
    }

    fun startRenameNotebook(item: NotebookListItem) {
        _dialog.value = FolderScreenDialog.RenameNotebook(item.notebook.id, item.notebook.title)
    }

    fun startDeleteSubfolder(item: SubfolderListItem) {
        _dialog.value = FolderScreenDialog.DeleteSubfolder(item.folder.id, item.folder.name)
    }

    fun pickTemplate(template: PageTemplate) = _dialog.update {
        if (it is FolderScreenDialog.NewNotebook) it.copy(template = template) else it
    }

    /** Deletes a notebook (hidden and kept) and offers Undo. */
    fun deleteNotebook(item: NotebookListItem) {
        viewModelScope.launch {
            if (notebooks.delete(item.notebook.id) is NotebookResult.Success) {
                _deletedNotebook.value = DeletedNotebook(item.notebook.id, item.notebook.title)
            }
        }
    }

    fun undoDeleteNotebook() {
        val deleted = _deletedNotebook.value ?: return
        _deletedNotebook.value = null
        viewModelScope.launch { notebooks.restore(deleted.notebookId) }
    }

    fun dismissDeletedNotebook() { _deletedNotebook.value = null }

    /** Archive (or bring back) this folder's course. Its tasks stay here either way. */
    fun setArchived(archived: Boolean) {
        val courseId = folder.value?.folder?.courseId ?: return
        viewModelScope.launch { folders.setCourseArchived(courseId, archived) }
    }

    fun dismissDialog() { _dialog.value = null }

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
            is FolderScreenDialog.NewSubfolder -> it.copy(name = text, error = null)
            is FolderScreenDialog.NewNotebook -> it.copy(title = text, error = null)
            is FolderScreenDialog.RenameSubfolder -> it.copy(name = text, error = null)
            is FolderScreenDialog.RenameNotebook -> it.copy(title = text, error = null)
            else -> it
        }
    }

    fun confirmDialog() {
        val dialog = _dialog.value ?: return
        if (saving) return // ignore a quick double tap
        saving = true
        viewModelScope.launch {
            try {
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
                    is FolderScreenDialog.NewSubfolder ->
                        finish(folders.createSubfolder(folderId, dialog.name)) { dialog.copy(error = it.message()) }
                    is FolderScreenDialog.RenameSubfolder ->
                        finish(folders.renameFolder(dialog.folderId, dialog.name)) { dialog.copy(error = it.message()) }
                    is FolderScreenDialog.DeleteSubfolder ->
                        finish(folders.deleteFolder(dialog.folderId)) { dialog.copy(error = it.message()) }
                    is FolderScreenDialog.NewNotebook ->
                        when (val result = notebooks.createNotebook(folderId, dialog.title, dialog.template)) {
                            is NotebookResult.Success -> {
                                _dialog.value = null
                                openNotebookEvents.send(result.id)
                            }
                            is NotebookResult.Failure -> _dialog.value = dialog.copy(error = result.error.message())
                        }
                    is FolderScreenDialog.RenameNotebook ->
                        when (val result = notebooks.rename(dialog.notebookId, dialog.title)) {
                            is NotebookResult.Success -> _dialog.value = null
                            is NotebookResult.Failure -> _dialog.value = dialog.copy(error = result.error.message())
                        }
                }
            } finally {
                saving = false
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

/** What each notebook error says on screen. */
fun NotebookError.message(): String = when (this) {
    NotebookError.BlankTitle -> "Give the notebook a name."
    NotebookError.NotFound -> "This notebook no longer exists."
    NotebookError.LastPage -> "A notebook needs at least one page."
    NotebookError.BadPdf -> "This PDF couldn't be opened."
}
