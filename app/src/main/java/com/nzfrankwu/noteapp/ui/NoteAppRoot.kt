package com.nzfrankwu.noteapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.nzfrankwu.noteapp.AppContainer
import com.nzfrankwu.noteapp.NoteAppApplication
import com.nzfrankwu.noteapp.ui.home.HomeScreen
import com.nzfrankwu.noteapp.ui.home.HomeViewModel
import com.nzfrankwu.noteapp.ui.navigation.HomeRoute
import com.nzfrankwu.noteapp.ui.navigation.SettingsRoute
import com.nzfrankwu.noteapp.ui.settings.SettingsScreen

/** The app's [AppContainer] (database, repositories), for creating ViewModels. */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as NoteAppApplication).container

/** The whole app UI: shows the screen on top of the back stack. */
@Composable
fun NoteAppRoot() {
    val container = appContainer()
    val backStack = rememberNavBackStack(HomeRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // Each screen on the back stack keeps its own saved state and its own ViewModels, which
        // are cleared when the screen is closed.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    viewModel = viewModel { HomeViewModel(container.folderRepository) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                    onOpenFolder = { /* The folder screen arrives in step 1.2. */ },
                )
            }
            entry<SettingsRoute> {
                SettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
