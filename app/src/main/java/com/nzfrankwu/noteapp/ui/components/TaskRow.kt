package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.domain.TaskLike
import com.nzfrankwu.noteapp.domain.TaskRules
import com.nzfrankwu.noteapp.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A simple task row: title, when it's due (or the target date), and its status.
 * Overdue shows in the error colour; a missed target date says "Past target".
 * (A richer task card with a tappable status chip comes with the task screens, F2.)
 */
@Composable
fun TaskRow(task: TaskLike, now: Instant, zone: ZoneId, modifier: Modifier = Modifier) {
    val overdue = TaskRules.isOverdue(task, now)
    val pastTarget = TaskRules.isPastTarget(task, now)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.TilePadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = whenText(task, zone, overdue, pastTarget),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = task.status.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val dateTime = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

private fun format(instant: Instant, zone: ZoneId) = instant.atZone(zone).format(dateTime).replace("AM", "am").replace("PM", "pm")

private fun whenText(task: TaskLike, zone: ZoneId, overdue: Boolean, pastTarget: Boolean): String {
    val due = task.dueAt?.let { "Due ${format(it, zone)}" }
    val target = task.targetAt?.let { "Target ${format(it, zone)}" }
    val parts = listOfNotNull(if (overdue) "Overdue" else null, if (pastTarget) "Past target" else null, due, target)
    return if (parts.isEmpty()) "No due date" else parts.joinToString(" · ")
}
