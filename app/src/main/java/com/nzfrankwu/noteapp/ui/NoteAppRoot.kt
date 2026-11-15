package com.nzfrankwu.noteapp.ui

import com.nzfrankwu.noteapp.ui.navigation.CoursesRoute
import com.nzfrankwu.noteapp.ui.settings.CoursesScreen
import com.nzfrankwu.noteapp.ui.settings.CoursesViewModel
import com.nzfrankwu.noteapp.ui.timeline.TimelineScreen
import com.nzfrankwu.noteapp.ui.timeline.TimelineViewModel
import com.nzfrankwu.noteapp.ui.calendar.CalendarScreen
import com.nzfrankwu.noteapp.ui.calendar.CalendarViewModel
import java.time.LocalDate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.reminders.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.nzfrankwu.noteapp.AppContainer
import com.nzfrankwu.noteapp.NoteAppApplication
import com.nzfrankwu.noteapp.ui.folder.FolderScreen
import com.nzfrankwu.noteapp.ui.folder.FolderViewModel
import com.nzfrankwu.noteapp.ui.home.HomeScreen
import com.nzfrankwu.noteapp.ui.navigation.FolderRoute
import com.nzfrankwu.noteapp.ui.navigation.NotebookRoute
import com.nzfrankwu.noteapp.ui.notebook.NotebookScreen
import com.nzfrankwu.noteapp.ui.notebook.NotebookViewModel
import com.nzfrankwu.noteapp.ui.home.HomeViewModel
import com.nzfrankwu.noteapp.ui.navigation.HomeRoute
import com.nzfrankwu.noteapp.ui.navigation.SettingsRoute
import com.nzfrankwu.noteapp.ui.navigation.TaskEditRoute
import com.nzfrankwu.noteapp.ui.navigation.CanvasLoginRoute
import com.nzfrankwu.noteapp.ui.navigation.LinkCoursesRoute
import com.nzfrankwu.noteapp.ui.canvas.LinkCoursesScreen
import com.nzfrankwu.noteapp.ui.canvas.LinkCoursesViewModel
import com.nzfrankwu.noteapp.ui.canvas.CanvasLoginScreen
import com.nzfrankwu.noteapp.ui.canvas.CanvasLoginViewModel
import com.nzfrankwu.noteapp.ui.settings.CanvasSettingsViewModel
import com.nzfrankwu.noteapp.ui.settings.SyncMessages
import com.nzfrankwu.noteapp.ui.navigation.TaskRoute
import com.nzfrankwu.noteapp.ui.settings.SettingsScreen
import com.nzfrankwu.noteapp.ui.task.TaskDetailScreen
import com.nzfrankwu.noteapp.ui.task.TaskDetailViewModel
import com.nzfrankwu.noteapp.ui.task.TaskEditorScreen
import com.nzfrankwu.noteapp.ui.task.TaskEditorViewModel
import com.nzfrankwu.noteapp.ui.theme.Dimens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.nzfrankwu.noteapp.BuildConfig
import com.nzfrankwu.noteapp.SharedText
import com.nzfrankwu.noteapp.domain.TaskRules

/**
 * Debug builds: sets a test reminder, 1 minute from now, for the task due soonest (so tapping it
 * can open a real task). Returns what to tell the user.
 */
private suspend fun sendTestReminder(container: AppContainer): String {
    val task = container.taskRepository.observeAllWithCourse().first()
        .map { it.task }
        .filter { !it.status.isFinished }
        .minWithOrNull(compareBy(nullsLast()) { TaskRules.effectiveDate(it) })
        ?: return "Add a task first: the test reminder opens a real task."
    container.reminderScheduler.scheduleTest(task.id)
    return "Test reminder for “${task.title}” in about 1 minute."
}

/** The app's [AppContainer] (database, repositories), for creating ViewModels. */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as NoteAppApplication).container

/** The whole app UI: shows the screen on top of the back stack. */
@Composable
fun NoteAppRoot(
    openTaskRequest: MutableStateFlow<String?> = MutableStateFlow(null),
    shareRequest: MutableStateFlow<SharedText?> = MutableStateFlow(null),
) {
    val container = appContainer()
    val context = LocalContext.current
    val backStack = rememberNavBackStack(HomeRoute)

    // A tapped reminder asks to open its task.
    val taskToOpen by openTaskRequest.collectAsStateWithLifecycle()
    LaunchedEffect(taskToOpen) {
        val taskId = taskToOpen ?: return@LaunchedEffect
        if (backStack.lastOrNull() != TaskRoute(taskId)) backStack.add(TaskRoute(taskId))
        openTaskRequest.value = null
    }

    // A link shared from another app opens a prefilled New task (nothing is saved until Save).
    val shared by shareRequest.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        val request = shared ?: return@LaunchedEffect
        shareRequest.value = null
        val prefill = container.quickAdd.prefill(request.text, request.subject)
        backStack.add(TaskEditRoute(courseId = prefill.courseId, title = prefill.title, url = prefill.url))
    }

    // Android 13+ asks before an app may show notifications. Noteapp asks the first time you save
    // a task with a date (the first time a reminder would matter). Android itself stops asking
    // after you've said no twice; Settings → Reminders then links to the system setting.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun askForNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canNotify(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // App-wide messages (e.g. "Task deleted · Undo"), shown above whichever screen is open.
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun back() { backStack.removeLastOrNull() }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(com.nzfrankwu.noteapp.ui.components.LocalThumbnails provides container.thumbnails) {
        NavDisplay(
            backStack = backStack,
            onBack = ::back,
            // Each screen on the back stack keeps its own saved state and its own ViewModels, which
            // are cleared when the screen is closed.
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeScreen(
                        viewModel = viewModel {
                            HomeViewModel(
                                container.folderRepository, container.taskRepository,
                                container.canvasSettings, container.canvasSyncRunner,
                                tabStore = container.viewSettings,
                            )
                        },
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onOpenFolder = { folderId -> backStack.add(FolderRoute(folderId)) },
                        onOpenTask = { taskId -> backStack.add(TaskRoute(taskId)) },
                        onAddTask = { backStack.add(TaskEditRoute()) },
                        onSignInAgain = { backStack.add(CanvasLoginRoute) },
                        onLinkCourses = { backStack.add(LinkCoursesRoute) },
                        calendar = {
                            CalendarScreen(
                                viewModel = viewModel { CalendarViewModel(container.taskRepository) },
                                onOpenTask = { taskId -> backStack.add(TaskRoute(taskId)) },
                                onAddTaskOnDay = { day -> backStack.add(TaskEditRoute(dueDate = day.toString())) },
                            )
                        },
                        timeline = {
                            TimelineScreen(
                                viewModel = viewModel { TimelineViewModel(container.taskRepository) },
                                onOpenTask = { taskId -> backStack.add(TaskRoute(taskId)) },
                            )
                        },
                        onShowMessage = { message ->
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                snackbar.showSnackbar(message)
                            }
                        },
                    )
                }
                entry<LinkCoursesRoute> {
                LinkCoursesScreen(
                    viewModel = viewModel { LinkCoursesViewModel(container.folderRepository, container.canvasSettings) },
                    onBack = ::back,
                )
            }
            entry<CanvasLoginRoute> {
                CanvasLoginScreen(
                    viewModel = viewModel { CanvasLoginViewModel(container.canvasSettings, container.canvasAccount) },
                    onClose = ::back,
                    onSignedIn = { name ->
                        back()
                        // Settings already shows "Signed in as …" and a spinner-free status; one
                        // message with the first sync's result, in order, once it's done.
                        scope.launch {
                            val message = SyncMessages.describe(container.canvasSyncRunner.run())
                            snackbar.currentSnackbarData?.dismiss()
                            snackbar.showSnackbar("Signed in as $name. $message")
                        }
                    },
                )
            }
            entry<TaskRoute> { route ->
                    TaskDetailScreen(
                        viewModel = viewModel { TaskDetailViewModel(route.taskId, container.taskRepository) },
                        onBack = ::back,
                        onEdit = { backStack.add(TaskEditRoute(taskId = route.taskId)) },
                        onDeleted = { task ->
                            back()
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val result = snackbar.showSnackbar(
                                    message = "Deleted “${task.title}”",
                                    actionLabel = "Undo",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) container.taskRepository.restoreTask(task)
                            }
                        },
                    )
                }
                entry<TaskEditRoute> { route ->
                    TaskEditorScreen(
                        viewModel = viewModel {
                            TaskEditorViewModel(
                                route.taskId, route.courseId, route.title, route.url, container.taskRepository,
                                prefillDueDate = route.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                            )
                        },
                        onClose = ::back,
                        onSaved = { taskId, hasDate ->
                            back()
                            if (hasDate) askForNotificationsIfNeeded()
                            // A new task opens its page, so you can see what you made; an edit just goes back.
                            if (route.taskId == null) backStack.add(TaskRoute(taskId))
                        },
                    )
                }
                entry<FolderRoute> { route ->
                    FolderScreen(
                        viewModel = viewModel {
                            FolderViewModel(route.folderId, container.folderRepository, container.taskRepository, container.notebookRepository)
                        },
                        onBack = ::back,
                        onOpenTask = { taskId -> backStack.add(TaskRoute(taskId)) },
                        onAddTask = { courseId -> backStack.add(TaskEditRoute(courseId = courseId)) },
                        onOpenFolder = { folderId -> backStack.add(FolderRoute(folderId)) },
                        onOpenNotebook = { notebookId -> backStack.add(NotebookRoute(notebookId)) },
                        calendar = { courseId ->
                            CalendarScreen(
                                viewModel = viewModel(key = "folder-calendar-$courseId") {
                                    CalendarViewModel(container.taskRepository, courseId = courseId)
                                },
                                onOpenTask = { taskId -> backStack.add(TaskRoute(taskId)) },
                                onAddTaskOnDay = { day ->
                                    backStack.add(TaskEditRoute(courseId = courseId, dueDate = day.toString()))
                                },
                            )
                        },
                        timeline = { courseId ->
                            TimelineScreen(
                                viewModel = viewModel(key = "folder-timeline-$courseId") {
                                    TimelineViewModel(container.taskRepository, courseId = courseId)
                                },
                                onOpenTask = { taskId -> backStack.add(TaskRoute(taskId)) },
                            )
                        },
                    )
                }
                entry<NotebookRoute> { route ->
                    NotebookScreen(
                        viewModel = viewModel {
                            NotebookViewModel(
                                route.notebookId, container.notebookRepository, container.notebookFiles,
                                container.inkSettings, container.appScope, container.thumbnails,
                            )
                        },
                        onBack = ::back,
                    )
                }
                entry<CoursesRoute> {
                    CoursesScreen(viewModel = viewModel { CoursesViewModel(container.folderRepository) }, onBack = ::back)
                }
                entry<SettingsRoute> {
                    SettingsScreen(
                    onBack = ::back,
                    canvas = viewModel {
                        CanvasSettingsViewModel(container.canvasSettings, container.canvasAccount, container.canvasSyncRunner)
                    },
                    onOpenCanvasBrowser = { backStack.add(CanvasLoginRoute) },
                    onOpenCourses = { backStack.add(CoursesRoute) },
                    inkSettings = container.inkSettings,
                    onTestReminder = if (BuildConfig.DEBUG) {
                        {
                            scope.launch {
                                val message = sendTestReminder(container)
                                snackbar.currentSnackbarData?.dismiss()
                                snackbar.showSnackbar(message)
                            }
                        }
                    } else {
                        null
                    },
                )
                }
            },
        )
        }
    SnackbarHost(
        hostState = snackbar,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Dimens.ScreenPadding),
    )
    }
}
