package com.nzfrankwu.noteapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.nzfrankwu.noteapp.ui.home.HomeScreen
import com.nzfrankwu.noteapp.ui.navigation.HomeRoute
import com.nzfrankwu.noteapp.ui.navigation.SettingsRoute
import com.nzfrankwu.noteapp.ui.settings.SettingsScreen

/** The whole app UI: shows the screen on top of the back stack. */
@Composable
fun NoteAppRoot() {
    val backStack = rememberNavBackStack(HomeRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(onOpenSettings = { backStack.add(SettingsRoute) })
            }
            entry<SettingsRoute> {
                SettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
