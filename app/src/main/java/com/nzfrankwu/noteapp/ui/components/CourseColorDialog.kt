package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.CourseColorChoice
import com.nzfrankwu.noteapp.ui.theme.LocalCourseColors

/**
 * Choose a course's colour (Phase 7, 7.7): the seven course colours, or "Automatic" (the next
 * free colour, as before). [current] is the chosen colour, or null when automatic.
 */
@Composable
fun CourseColorDialog(
    code: String,
    current: CourseColorChoice?,
    onPick: (CourseColorChoice?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalCourseColors.current.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour for $code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CourseColorChoice.entries.forEach { choice ->
                        val color = palette[choice.slot % palette.size].accent
                        val picked = choice == current
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .background(color, CircleShape)
                                .border(
                                    if (picked) 3.dp else 0.dp,
                                    if (picked) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable { onPick(choice) }
                                .semantics {
                                    contentDescription = choice.label
                                    selected = picked
                                }
                                .testTag("course-color-${choice.key}"),
                        ) {
                            if (picked) Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Text(
                    "Automatic gives each new course the next colour no other course is using.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null) }, modifier = Modifier.testTag("course-color-auto")) {
                Text(if (current == null) "✓ Automatic" else "Automatic")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
