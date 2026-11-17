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

/** Settings → Courses: archive or bring back courses. */
@Serializable
data object CoursesRoute : NavKey

/** Recently deleted notebooks (PHASE-6.md 6.4). */
@Serializable
data object RecentlyDeletedRoute : NavKey

@Serializable
data class FolderRoute(val folderId: String) : NavKey

/**
 * A notebook open for writing (Phase 2). With [pageId] it opens at that page, zoomed to [spot]
 * when given (a bookmark, Phase 4); the defaults keep saved back stacks loading.
 */
@Serializable
data class NotebookRoute(
    val notebookId: String,
    val pageId: String? = null,
    val spot: com.nzfrankwu.noteapp.domain.PageSpot? = null,
) : NavKey

/** One task's details. */
@Serializable
data class TaskRoute(val taskId: String) : NavKey

/**
 * The task form. With [taskId] it edits that task; without, it's "New task", optionally
 * prefilled with a course (from a folder), a title and link (shared from another app), or a due
 * date ([dueDate], e.g. "2026-11-20", from "Add task on this day" in the calendar).
 */
@Serializable
data class TaskEditRoute(
    val taskId: String? = null,
    val courseId: String? = null,
    val title: String? = null,
    val url: String? = null,
    val dueDate: String? = null,
) : NavKey

/** Signing in to Canvas in the in-app browser. */
@Serializable
data object CanvasLoginRoute : NavKey

/** Giving imported Canvas courses a folder. */
@Serializable
data object LinkCoursesRoute : NavKey
