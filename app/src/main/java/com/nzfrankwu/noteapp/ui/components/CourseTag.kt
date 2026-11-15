package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.ui.theme.LocalCourseColors

/** How a course is named in lists: "ELECTENG 204 · Engineering Electromagnetics", or just the code. */
fun courseLabel(course: Course): String =
    if (course.name.isNullOrBlank() || course.name == course.code) course.code else "${course.code} · ${course.name}"

/**
 * A course code in its course's colour, with a small colour bar in front: "▌ELECTENG 204".
 * [code] null means the task has no course; [label] is then shown in grey (e.g. "No course").
 */
@Composable
fun CourseTag(
    code: String?,
    modifier: Modifier = Modifier,
    label: String = code ?: "No course",
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val colors = LocalCourseColors.current.of(code)
    Row(
        modifier = modifier.testTag("course-tag"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 14.dp)
                .background(colors.accent, RoundedCornerShape(2.dp)),
        )
        Text(label, style = style, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
