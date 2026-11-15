package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.domain.CourseCodes

/**
 * The course field of "New folder" and "Link a course" (PHASE-1B.md, B.9): a list of [courses]
 * (current courses without a folder), plus [noneLabel] (if given) and "Other course code…" for a
 * course Noteapp doesn't know yet. The dialog shows its code box while [typingOther] is true.
 *
 * Picking a course just fills in its code ([selectedCode]); the folder rules stay the same.
 */
@Composable
fun CourseChoiceField(
    courses: List<Course>,
    selectedCode: String,
    typingOther: Boolean,
    onPick: (Course?) -> Unit,
    onTypeOther: () -> Unit,
    modifier: Modifier = Modifier,
    /** The "no course" option's label, or null when a course is required (Link a course). */
    noneLabel: String? = "No course",
) {
    var open by remember { mutableStateOf(false) }
    val picked = courses.firstOrNull { CourseCodes.matchKey(it.code) == CourseCodes.matchKey(selectedCode) }
    val shown = when {
        typingOther -> "Other course code…"
        selectedCode.isBlank() -> noneLabel.orEmpty()
        picked != null -> courseLabel(picked)
        else -> selectedCode
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fieldWidth = maxWidth
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            label = { Text("Course") },
            placeholder = { Text("Pick a course") },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field is read-only; this transparent layer makes the whole field open the list.
        Box(Modifier.matchParentSize().clickable { open = true }.testTag("course-choice"))
        // As wide as the field, so course names aren't cut short.
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.width(fieldWidth)) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = { open = false; onPick(null) })
            }
            courses.forEach { course ->
                DropdownMenuItem(
                    text = { CourseTag(course.code, label = courseLabel(course), style = MaterialTheme.typography.bodyLarge) },
                    onClick = { open = false; onPick(course) },
                    modifier = Modifier.testTag("course-choice-${course.code}"),
                )
            }
            if (courses.isNotEmpty() || noneLabel != null) HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Other course code…") },
                onClick = { open = false; onTypeOther() },
                modifier = Modifier.testTag("course-choice-other"),
            )
        }
    }
}
