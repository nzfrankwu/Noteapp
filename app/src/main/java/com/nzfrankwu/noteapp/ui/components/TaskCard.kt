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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.db.LinkedNotebook
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.DateText
import com.nzfrankwu.noteapp.domain.TaskRules
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId

/**
 * Every task's linked notebooks (by task ID) and how to open one, for the chips on task cards
 * (PHASE-4.md 4.9). Provided once for the whole app; screens and tests without it show no chips.
 */
class TaskNotebooks(val byTask: Map<String, List<LinkedNotebook>>, val open: (notebookId: String) -> Unit)

val LocalTaskNotebooks = compositionLocalOf { TaskNotebooks(emptyMap()) {} }

/**
 * One task in a list: title, course, when it's due, and its status tag.
 * Tapping the card opens the task; tapping the status tag changes the status in place.
 * Overdue tasks get a red badge; a missed "My target date" says "Past target".
 * Linked notebooks show as small chips under it; tapping one opens that notebook (Phase 4).
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
        val linked = LocalTaskNotebooks.current
        val notebooks = linked.byTask[task.id].orEmpty()
        if (compact) {
            Column(padding, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskCardDetails(task, courseCode, now, zone, showCourse)
                NotebookChips(notebooks, linked.open)
                StatusChip(task.status, onStatusChange)
            }
        } else {
            Row(padding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.ItemGap)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskCardDetails(task, courseCode, now, zone, showCourse)
                    NotebookChips(notebooks, linked.open)
                }
                StatusChip(task.status, onStatusChange)
            }
        }
    }
}

/** The task's linked notebooks as small chips (at most [MAX_CHIPS], then "+N"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotebookChips(notebooks: List<LinkedNotebook>, onOpen: (String) -> Unit) {
    if (notebooks.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        notebooks.take(MAX_CHIPS).forEach { nb ->
            // Plain clickable (not Surface(onClick)): no 48 dp minimum height, so cards stay compact.
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onOpen(nb.notebookId) }
                    .testTag("notebook-chip-${nb.title}"),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_note), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp),
                    )
                    Text(
                        nb.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp).widthIn(max = 200.dp),
                    )
                }
            }
        }
        if (notebooks.size > MAX_CHIPS) {
            Text(
                "+${notebooks.size - MAX_CHIPS}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically).padding(vertical = 4.dp),
            )
        }
    }
}

private const val MAX_CHIPS = 3

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
            if (showCourse) CourseTag(courseCode)
            if (overdue) OverdueBadge()
            if (pastTarget) InfoBadge("Past target")
            if (task.removedFromCanvas) InfoBadge("Removed from Canvas")
            DaysLeftText(task, now, zone)
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
