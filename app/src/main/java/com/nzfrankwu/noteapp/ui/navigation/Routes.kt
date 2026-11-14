package com.nzfrankwu.noteapp.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Every screen in the app is a "route". Navigation 3 keeps a list of routes (the back stack):
// opening a screen adds its route, Back removes the last one. Routes are @Serializable so the
// back stack survives rotation and the app being paused in the background.

@Serializable
data object HomeRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data class FolderRoute(val folderId: String) : NavKey
