package com.nzfrankwu.noteapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.DateText
import com.nzfrankwu.noteapp.domain.TaskRules
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId

/**
 * One task in a list: title, course, when it's due, and its status tag.
 * Tapping the card opens the task; tapping the status tag changes the status in place.
 * Overdue tasks get a red badge; a missed "My target date" says "Past target".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: Task,
    courseCode: String?,
    now: Instant,
    zone: ZoneId,
    onClick: () -> Unit,
    onStatusChange: (TaskStatus) -> Unit,
    modifier: Modifier = Modifier,
    /** False inside a course folder, where the course is obvious. Due soon shows it ("No course" if none). */
    showCourse: Boolean = true,
    /**
     * True on a narrow list (e.g. Due soon in portrait): the status tag goes under the text so the
     * dates get the full width. The list decides this once for all its cards
     * (see [Dimens.TaskCardNarrowWidth]).
     */
    compact: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("task-card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        val padding = Modifier.padding(horizontal = Dimens.TilePadding, vertical = Dimens.TaskCardVerticalPadding)
        if (compact) {
            Column(padding, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskCardDetails(task, courseCode, now, zone, showCourse)
                StatusChip(task.status, onStatusChange)
            }
        } else {
            Row(padding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.ItemGap)) {
                Column(Modifier.weight(1f)) { TaskCardDetails(task, courseCode, now, zone, showCourse) }
                StatusChip(task.status, onStatusChange)
            }
        }
    }
}

/** Title, course, badges and dates of a task card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCardDetails(task: Task, courseCode: String?, now: Instant, zone: ZoneId, showCourse: Boolean) {
    val overdue = TaskRules.isOverdue(task, now)
    val pastTarget = TaskRules.isPastTarget(task, now)
    val finished = task.status.isFinished
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
            color = if (finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCourse) {
                Text(
                    text = courseCode ?: "No course",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (overdue) OverdueBadge()
            if (pastTarget) InfoBadge("Past target")
            if (task.removedFromCanvas) InfoBadge("Removed from Canvas")
            Text(
                text = TaskCardText.whenText(task, now, zone),
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The "when" line of a task card. */
object TaskCardText {
    /**
     * "Due Fri 20 Nov, 11:59 pm · Target Wed 18 Nov, 9:00 am", or "No due date".
     * Each part uses non-breaking spaces, so on a narrow card a line only breaks between the
     * due and target parts, never inside a date ("11:59 / pm").
     */
    fun whenText(task: Task, now: Instant, zone: ZoneId): String {
        val due = task.dueAt?.let { "Due ${DateText.dateTime(it, zone, now)}" }
        val target = task.targetAt?.let { "Target ${DateText.dateTime(it, zone, now)}" }
        return listOfNotNull(due, target).joinToString(" · ") { it.replace(' ', NBSP) }.ifEmpty { "No due date" }
    }

    private const val NBSP = '\u00A0'

}
