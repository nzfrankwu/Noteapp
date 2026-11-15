package com.nzfrankwu.noteapp.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.nzfrankwu.noteapp.domain.DaysLeft
import com.nzfrankwu.noteapp.domain.DaysLeftUrgency
import com.nzfrankwu.noteapp.domain.TaskLike
import com.nzfrankwu.noteapp.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.ZoneId

/**
 * "3 days left", "Due tomorrow" (amber) or "2 days overdue" (red) for a dated, unfinished task.
 * Shows nothing for undated or finished tasks (see [DaysLeft.of]).
 */
@Composable
fun DaysLeftText(
    task: TaskLike,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val daysLeft = DaysLeft.of(task, now, zone) ?: return
    Text(
        text = daysLeft.text,
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = when (daysLeft.urgency) {
            DaysLeftUrgency.OVERDUE -> MaterialTheme.colorScheme.error
            DaysLeftUrgency.SOON -> LocalStatusColors.current.dueSoonText
            DaysLeftUrgency.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        modifier = modifier.testTag("days-left"),
    )
}
