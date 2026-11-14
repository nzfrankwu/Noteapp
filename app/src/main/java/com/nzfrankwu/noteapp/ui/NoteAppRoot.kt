package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
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
import com.nzfrankwu.noteapp.ui.home.HomeViewModel
import com.nzfrankwu.noteapp.ui.navigation.HomeRoute
import com.nzfrankwu.noteapp.ui.navigation.SettingsRoute
import com.nzfrankwu.noteapp.ui.navigation.TaskEditRoute
import com.nzfrankwu.noteapp.ui.navigation.TaskRoute
import com.nzfrankwu.noteapp.ui.settings.SettingsScreen
import com.nzfrankwu.noteapp.ui.task.TaskDetailScreen
import com.nzfrankwu.noteapp.ui.task.TaskDetailViewModel
import com.nzfrankwu.noteapp.ui.task.TaskEditorScreen
import com.nzfrankwu.noteapp.ui.task.TaskEditorViewModel
import com.nzfrankwu.noteapp.ui.theme.Dimens
import kotlinx.coroutines.launch

/** The app's [AppContainer] (database, repositories), for creating ViewModels. */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as NoteAppApplication).container

/** The whole app UI: shows the screen on top of the back stack. */
@Composable
fun NoteAppRoot() {
    val container = appContainer()
    val backStack = rememberNavBackStack(HomeRoute)
    // App-wide messages (e.g. "Task deleted · Undo"), shown above whichever screen is open.
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun back() { backStack.removeLastOrNull() }

    Box(Modifier.fillMaxSize()) {
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
                        viewModel = viewModel { HomeViewModel(container.folderRepository, container.taskRepository) },
                        onOpenSettings = { backStack.add(SettingsRoute) },
                        onOpenFolder = { folderId -> backStack.add(FolderRoute(folderId)) },
                        onOpenTask = { taskId -> backStack.add(TaskRoute(taskId)) },
                        onAddTask = { backStack.add(TaskEditRoute()) },
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
                            TaskEditorViewModel(route.taskId, route.courseId, route.title, route.url, container.taskRepository)
                        },
                        onClose = ::back,
                        onSaved = { taskId ->
                            back()
                            // A new task opens its page, so you can see what you made; an edit just goes back.
                            if (route.taskId == null) backStack.add(TaskRoute(taskId))
                        },
                    )
                }
                entry<FolderRoute> { route ->
                    FolderScreen(
                        viewModel = viewModel {
                            FolderViewModel(route.folderId, container.folderRepository, container.taskRepository)
                        },
                        onBack = ::back,
                        onOpenTask = { taskId -> backStack.add(TaskRoute(taskId)) },
                        onAddTask = { courseId -> backStack.add(TaskEditRoute(courseId = courseId)) },
                    )
                }
                entry<SettingsRoute> {
                    SettingsScreen(onBack = ::back)
                }
            },
        )
    SnackbarHost(
        hostState = snackbar,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Dimens.ScreenPadding),
    )
    }
}
