package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.TaskChoices
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.DaysLeft
import com.nzfrankwu.noteapp.ui.components.CourseTag
import com.nzfrankwu.noteapp.ui.components.DaysLeftText
import com.nzfrankwu.noteapp.ui.task.PickerDialog
import com.nzfrankwu.noteapp.ui.task.PickerEmpty
import com.nzfrankwu.noteapp.ui.task.PickerHeading
import com.nzfrankwu.noteapp.ui.theme.EditorBarButton
import com.nzfrankwu.noteapp.ui.theme.EditorBarIcon
import java.time.Instant
import java.time.ZoneId

/**
 * The chip next to Back (PHASE-4.md 4.3): the linked task and its days left, or "2 tasks".
 * Tapping it lists the tasks (open one, or unlink it) and offers ＋ Link to a task….
 * Nothing shows when the notebook has no linked task.
 */
@Composable
fun LinkedTaskChip(
    tasks: List<TaskWithCourse>,
    now: Instant,
    zone: ZoneId,
    onOpenTask: (String) -> Unit,
    onUnlink: (TaskWithCourse) -> Unit,
    onLinkTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tasks.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        val single = tasks.singleOrNull()
        val label = single?.let { t ->
            listOfNotNull(t.task.title, DaysLeft.of(t.task, now, zone)?.text).joinToString(" · ")
        } ?: "${tasks.size} tasks"
        Row(
            Modifier
                .height(40.dp)
                .widthIn(max = 360.dp)
                .shadow(3.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(EditorBarButton)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                .clickable { open = true }
                .padding(horizontal = 14.dp)
                .testTag("linked-task-chip"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_task), contentDescription = null, tint = EditorBarIcon, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = EditorBarIcon,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            tasks.forEach { t ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(t.task.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 320.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CourseTag(t.courseCode, style = MaterialTheme.typography.labelMedium)
                                DaysLeftText(t.task, now, zone)
                            }
                        }
                    },
                    onClick = { open = false; onOpenTask(t.task.id) },
                    trailingIcon = {
                        IconButton(onClick = { open = false; onUnlink(t) }) {
                            Icon(painterResource(R.drawable.ic_link_off), contentDescription = "Unlink ${t.task.title}")
                        }
                    },
                    modifier = Modifier.testTag("linked-task-${t.task.title}"),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Link to a task…") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                onClick = { open = false; onLinkTask() },
                modifier = Modifier.testTag("chip-link-task"),
            )
        }
    }
}

/** "Link to a task": this course's tasks first, then "Other tasks ▸". Already linked tasks aren't offered. */
@Composable
fun LinkTaskSheet(choices: TaskChoices, now: Instant, zone: ZoneId, onPick: (TaskWithCourse) -> Unit, onCancel: () -> Unit) {
    PickerDialog(title = "Link to a task", testTag = "link-task-sheet", onCancel = onCancel) {
        taskChoices(choices, now, zone, onPick, emptyText = "No other tasks to link.")
    }
}

/** The task rows the task pickers share: [TaskChoices.inCourse], then a fold-out "Other tasks". */
fun LazyListScope.taskChoices(
    choices: TaskChoices,
    now: Instant,
    zone: ZoneId,
    onPick: (TaskWithCourse) -> Unit,
    emptyText: String,
    inCourseHeading: String = "This course",
) {
    if (choices.isEmpty) {
        item(key = "empty") { PickerEmpty(emptyText) }
        return
    }
    if (choices.inCourse.isNotEmpty()) {
        item(key = "course-h") { PickerHeading(inCourseHeading, 0) }
        items(choices.inCourse, key = { "c-" + it.task.id }) { TaskPickRow(it, now, zone) { onPick(it) } }
    }
    if (choices.others.isNotEmpty()) {
        item(key = "others") { OtherTasks(choices, now, zone, onPick) }
    }
}

@Composable
private fun OtherTasks(choices: TaskChoices, now: Instant, zone: ZoneId, onPick: (TaskWithCourse) -> Unit) {
    var show by remember(choices.inCourse.isEmpty()) { mutableStateOf(choices.inCourse.isEmpty()) }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable { show = !show }.padding(vertical = 12.dp, horizontal = 4.dp).testTag("other-tasks"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Other tasks", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).rotate(if (show) 90f else 0f),
            )
        }
        if (show) choices.others.forEach { TaskPickRow(it, now, zone) { onPick(it) } }
    }
}

@Composable
fun TaskPickRow(item: TaskWithCourse, now: Instant, zone: ZoneId, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)
            .testTag("pick-task-${item.task.title}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(painterResource(R.drawable.ic_task), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(item.task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CourseTag(item.courseCode, style = MaterialTheme.typography.labelMedium)
                DaysLeftText(item.task, now, zone)
            }
        }
    }
}
