package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.db.Course

/** "All courses ▾": narrows the calendar or timeline to one course. */
@Composable
fun CourseFilterButton(
    courses: List<Course>,
    selectedCourseId: String?,
    onSelect: (courseId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val selected = courses.firstOrNull { it.id == selectedCourseId }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            modifier = Modifier.testTag("course-filter"),
        ) {
            if (selected == null) Text("All courses", maxLines = 1) else CourseTag(selected.code)
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("All courses") },
                onClick = {
                    open = false
                    onSelect(null)
                },
            )
            courses.forEach { course ->
                DropdownMenuItem(
                    text = { CourseTag(course.code, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        open = false
                        onSelect(course.id)
                    },
                    modifier = Modifier.testTag("course-filter-${course.code}"),
                )
            }
        }
    }
}

/** "☐ Show finished": adds Done tasks (shown faded). */
@Composable
fun ShowFinishedCheckbox(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(end = 8.dp)
            .testTag("show-finished-toggle"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
        Text("Show finished", style = MaterialTheme.typography.labelLarge)
    }
}
