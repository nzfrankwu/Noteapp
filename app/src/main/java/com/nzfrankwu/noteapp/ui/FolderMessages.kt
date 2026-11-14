package com.nzfrankwu.noteapp.ui

import com.nzfrankwu.noteapp.data.FolderError

/** What each folder error says on screen (one wording everywhere). */
fun FolderError.message(): String = when (this) {
    FolderError.BlankName -> "Give the folder a name."
    FolderError.BlankCourseCode -> "Type a course code, e.g. ELECTENG 204."
    FolderError.NotFound -> "This folder no longer exists."
    is FolderError.CourseAlreadyLinked -> "$courseCode is already linked to the folder “$folderName”."
}
