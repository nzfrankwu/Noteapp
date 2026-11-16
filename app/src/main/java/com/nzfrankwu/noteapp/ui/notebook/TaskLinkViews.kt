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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.nzfrankwu.noteapp.data.CourseFilter
import com.nzfrankwu.noteapp.data.DueFilter
import com.nzfrankwu.noteapp.data.LinkChoices
import com.nzfrankwu.noteapp.data.TaskFilter
import com.nzfrankwu.noteapp.data.TaskChoices
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.DaysLeft
import com.nzfrankwu.noteapp.ui.components.CourseTag
import com.nzfrankwu.noteapp.ui.components.DaysLeftText
import com.nzfrankwu.noteapp.ui.components.PickerDialog
import com.nzfrankwu.noteapp.ui.components.PickerEmpty
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

/**
 * "Link to a task" (PHASE-5.md FB3): a search field, the course and due-date filters, and the
 * matching tasks. Already linked tasks aren't offered.
 */
@Composable
fun LinkTaskSheet(choices: TaskChoices, now: Instant, zone: ZoneId, onPick: (TaskWithCourse) -> Unit, onCancel: () -> Unit) {
    var filter by remember { mutableStateOf(TaskFilter()) }
    val shown = remember(choices, filter) { LinkChoices.filter(choices, filter, now) }
    PickerDialog(title = "Link to a task", testTag = "link-task-sheet", onCancel = onCancel) {
        item(key = "search") { TaskSearch(filter, { filter = it }, choices) }
        if (shown.isEmpty()) {
            item(key = "empty") { PickerEmpty(if (choices.isEmpty) "No other tasks to link." else "No tasks match.") }
        }
        items(shown, key = { "t-" + it.task.id }) { TaskPickRow(it, now, zone) { onPick(it) } }
    }
}

/**
 * The task pickers' search field and filters (PHASE-5.md FB3): course (this course, all other
 * courses, or one course) and due date (due soon, or already due).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSearch(filter: TaskFilter, onFilter: (TaskFilter) -> Unit, choices: TaskChoices, modifier: Modifier = Modifier) {
    val noCourse = choices.courseId == null
    val thisCode = choices.inCourse.firstOrNull()?.courseCode ?: "This course"
    fun label(c: CourseFilter): String = when (c) {
        CourseFilter.ThisCourse -> if (noCourse) "All courses" else thisCode
        CourseFilter.OtherCourses -> if (noCourse) "All courses" else "All other courses"
        is CourseFilter.One -> c.code
    }
    val courses = remember(choices) { LinkChoices.courses(choices) }
    Column(modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = { onFilter(filter.copy(query = it)) },
            placeholder = { Text("Search tasks") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (filter.query.isEmpty()) null else {
                {
                    IconButton(onClick = { onFilter(filter.copy(query = "")) }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear the search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("task-search"),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var open by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = true,
                    onClick = { open = true },
                    colors = chipColors(),
                    border = chipBorder(true),
                    label = { Text(label(filter.course), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) },
                    trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("filter-course"),
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    val options = buildList {
                        add(CourseFilter.ThisCourse)
                        if (!noCourse) add(CourseFilter.OtherCourses)
                        addAll(courses)
                    }
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(label(option)) },
                            onClick = { open = false; onFilter(filter.copy(course = option)) },
                            trailingIcon = if (option == filter.course) {
                                { Icon(painterResource(R.drawable.ic_check), contentDescription = "Chosen") }
                            } else {
                                null
                            },
                            modifier = Modifier.testTag("course-option-${label(option)}"),
                        )
                    }
                }
            }
            FilterChip(
                selected = filter.due == DueFilter.SOON,
                onClick = { onFilter(filter.copy(due = DueFilter.SOON)) },
                label = { Text("Due soon") },
                leadingIcon = if (filter.due == DueFilter.SOON) chipCheck else null,
                colors = chipColors(),
                border = chipBorder(filter.due == DueFilter.SOON),
                modifier = Modifier.testTag("filter-soon"),
            )
            FilterChip(
                selected = filter.due == DueFilter.PAST,
                onClick = { onFilter(filter.copy(due = DueFilter.PAST)) },
                label = { Text("Already due") },
                leadingIcon = if (filter.due == DueFilter.PAST) chipCheck else null,
                colors = chipColors(),
                border = chipBorder(filter.due == DueFilter.PAST),
                modifier = Modifier.testTag("filter-past"),
            )
        }
    }
}

/** Filter chips that read clearly on the dialog's background: filled when on, outlined when off. */
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
private fun chipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outline,
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    selectedBorderWidth = 1.dp,
)

private val chipCheck: @Composable () -> Unit = {
    Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp))
}

/**
 * One task in a picker: its title, course and days left (or "Done"). [selected] (the bookmark
 * sheet's choice, FB1) shows a check mark and a stronger background.
 */
@Composable
fun TaskPickRow(item: TaskWithCourse, now: Instant, zone: ZoneId, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp, end = 8.dp)
            .semantics { this.selected = selected }
            .testTag("pick-task-${item.task.title}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            painterResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_task),
            contentDescription = if (selected) "Chosen" else null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else null,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CourseTag(item.courseCode, style = MaterialTheme.typography.labelMedium)
                if (item.task.status.isFinished) {
                    Text("Done", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DaysLeftText(item.task, now, zone)
                }
            }
        }
    }
}
