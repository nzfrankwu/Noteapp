package com.nzfrankwu.noteapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.FolderError
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.db.FolderListItem
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.CanvasSettings
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.canvas.CanvasSyncRunner
import com.nzfrankwu.noteapp.ui.settings.SyncMessages
import com.nzfrankwu.noteapp.ui.message
import com.nzfrankwu.noteapp.ui.minuteTicker
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.domain.HomeTab
import com.nzfrankwu.noteapp.data.HomeTabStore
import com.nzfrankwu.noteapp.data.InMemoryHomeTabStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the strips at the top of home show. */
data class HomeBanners(
    val signInExpired: Boolean = false,
    /** Canvas courses with no folder that you haven't said "Not now" to. */
    val newCourses: List<Course> = emptyList(),
)

/** Which folder dialog is open on the home screen, with what's typed in it. */
sealed interface FolderDialog {
    data class New(
        val name: String = "",
        val courseCode: String = "",
        val error: String? = null,
        /** True after "Other course code…": the code box is shown. */
        val typingOtherCode: Boolean = false,
    ) : FolderDialog
    data class Rename(val folderId: String, val name: String, val error: String? = null) : FolderDialog
    data class ConfirmDelete(val folderId: String, val name: String, val courseCode: String?) : FolderDialog
}

/**
 * The home screen's state: Due soon and the folder list (live from the database), and the open dialog.
 * Survives rotation, so a half-typed dialog isn't lost.
 */
class HomeViewModel(
    private val folderRepository: FolderRepository,
    private val taskRepository: TaskRepository,
    /** Canvas pieces; null in screen tests that don't need Canvas. */
    private val canvasSettings: CanvasSettings? = null,
    private val syncRunner: CanvasSyncRunner? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** Where the last home tab is remembered (in memory in screen tests). */
    private val tabStore: HomeTabStore = InMemoryHomeTabStore(),
) : ViewModel() {

    /** The open home tab: Overview, Calendar or Timeline. Null for the moment before it's loaded. */
    val tab: StateFlow<HomeTab?> = tabStore.homeTab
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    fun selectTab(tab: HomeTab) {
        viewModelScope.launch { tabStore.setHomeTab(tab) }
    }

    /** The strips across the top of home: expired Canvas sign-in, and new courses to link. */
    val banners: StateFlow<HomeBanners> = if (canvasSettings == null) {
        MutableStateFlow(HomeBanners())
    } else {
        combine(
            canvasSettings.signedInAs,
            canvasSettings.signInExpired,
            folderRepository.observeCanvasCoursesWithoutFolder(),
            canvasSettings.hiddenLinkPrompts,
        ) { name, expired, unlinked, hidden ->
            HomeBanners(signInExpired = name != null && expired, newCourses = unlinked.filter { it.id !in hidden })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeBanners())
    }

    /** "Not now" on the new-courses strip: stop suggesting these courses. */
    fun hideNewCourses() {
        val ids = banners.value.newCourses.map { it.id }
        viewModelScope.launch { canvasSettings?.hideLinkPrompts(ids) }
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** A message for the app's snackbar (e.g. the result of pull-to-refresh). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun messageShown() { _message.value = null }

    /** Pull-to-refresh: sync with Canvas now. */
    fun refresh() {
        val runner = syncRunner ?: return
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                _message.value = SyncMessages.describe(runner.run())
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** All folders A–Z, or null while they're still loading. */
    val folders: StateFlow<List<FolderListItem>?> = folderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /** Whether the "Archived (N)" row under Folders is open. */
    private val _archivedExpanded = MutableStateFlow(false)
    val archivedExpanded: StateFlow<Boolean> = _archivedExpanded.asStateFlow()
    fun toggleArchived() = _archivedExpanded.update { !it }

    /** Archive (or bring back) a folder's course. Its tasks leave (or rejoin) Due soon, Calendar and Timeline. */
    fun setArchived(item: FolderListItem, archived: Boolean) {
        val courseId = item.folder.courseId ?: return
        viewModelScope.launch {
            folderRepository.setCourseArchived(courseId, archived)
            val code = item.courseCode ?: item.folder.name
            _message.value = if (archived) {
                "$code archived. Its tasks are hidden from Home; the folder is under Archived."
            } else {
                "$code is back with your current courses."
            }
        }
    }

    private val noDueDateExpanded = MutableStateFlow(false)
    private val showFinished = MutableStateFlow(false)

    /** Due soon across all current courses (archived courses' tasks are left out). */
    val dueSoon: StateFlow<DueSoonState> =
        combine(taskRepository.observeActiveWithCourse(), minuteTicker(clock), noDueDateExpanded, showFinished) { tasks, now, expanded, finished ->
            DueSoonState.build(tasks, now, zone, expanded, finished)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DueSoonState())

    fun toggleNoDueDate() = noDueDateExpanded.update { !it }
    fun toggleShowFinished() = showFinished.update { !it }

    fun setStatus(taskId: String, status: TaskStatus) {
        viewModelScope.launch { taskRepository.setStatus(taskId, status) }
    }

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

    /** Current courses without a folder: the choices in New folder. */
    val coursesWithoutFolder: StateFlow<List<Course>> = folderRepository.observeCoursesWithoutFolder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A course from the list (or null for "No course"). An empty name becomes the course's name. */
    fun pickCourse(course: Course?) = _dialog.update {
        if (it !is FolderDialog.New) return@update it
        val name = if (it.name.isBlank() && course != null) course.name?.takeIf(String::isNotBlank) ?: course.code else it.name
        it.copy(name = name, courseCode = course?.code.orEmpty(), typingOtherCode = false, error = null)
    }

    /** "Other course code…": type a course Noteapp doesn't know yet. */
    fun typeOtherCourse() = _dialog.update {
        if (it is FolderDialog.New) it.copy(courseCode = "", typingOtherCode = true, error = null) else it
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
                        finish(result) { error -> dialog.copy(error = error.message()) }
                    }
                    is FolderDialog.Rename -> {
                        val result = folderRepository.renameFolder(dialog.folderId, dialog.name)
                        finish(result) { error -> dialog.copy(error = error.message()) }
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
}
