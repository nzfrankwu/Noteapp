package com.nzfrankwu.noteapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.ui.components.CourseChoiceField

/**
 * "New folder" (name + optional course) or "Rename folder" (name only, when [onCourseCodeChange]
 * is null). [error] shows in red under the fields.
 *
 * The course is picked from [courseChoices] (current courses without a folder; B.9), or typed
 * after "Other course code…". With no courses to pick from, the code box shows straight away.
 */
@Composable
fun FolderEditDialog(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    courseCode: String,
    onCourseCodeChange: ((String) -> Unit)?,
    error: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    courseChoices: List<Course> = emptyList(),
    typingOtherCode: Boolean = false,
    onPickCourse: (Course?) -> Unit = {},
    onTypeOtherCourse: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .testTag("folder-name-field"),
                )
                if (onCourseCodeChange != null && courseChoices.isNotEmpty()) {
                    CourseChoiceField(
                        courses = courseChoices,
                        selectedCode = courseCode,
                        typingOther = typingOtherCode,
                        onPick = onPickCourse,
                        onTypeOther = onTypeOtherCourse,
                    )
                }
                if (onCourseCodeChange != null && (courseChoices.isEmpty() || typingOtherCode)) {
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = onCourseCodeChange,
                        label = { Text("Course code") },
                        supportingText = { Text("Optional. Links the folder to a course, e.g. ELECTENG 204") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("folder-code-field"),
                    )
                }
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("dialog-confirm"),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Asks before deleting a folder, and says that the course's tasks are kept. */
@Composable
fun DeleteFolderDialog(
    name: String,
    courseCode: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Why the folder couldn't be deleted (e.g. it isn't empty), shown instead of the question. */
    error: String? = null,
) {
    if (error != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Can't delete “$name”") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("dialog-ok")) { Text("OK") }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete “$name”?") },
        text = {
            Text(
                if (courseCode != null) {
                    "Only the folder is removed. Tasks for $courseCode are kept, and you can " +
                        "link the course to a new folder later."
                } else {
                    "Only the folder is removed."
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("dialog-confirm"),
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
