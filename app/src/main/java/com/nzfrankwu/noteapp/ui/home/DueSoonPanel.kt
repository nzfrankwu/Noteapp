package com.nzfrankwu.noteapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.DueGroup
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.components.EmptyState
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.components.TaskCard
import com.nzfrankwu.noteapp.ui.theme.Dimens

/** What the Due soon panel can do. */
class DueSoonActions(
    val onAddTask: () -> Unit = {},
    val onOpenTask: (taskId: String) -> Unit = {},
    val onStatusChange: (taskId: String, TaskStatus) -> Unit = { _, _ -> },
    val onToggleNoDueDate: () -> Unit = {},
    val onToggleShowFinished: () -> Unit = {},
)

/** One line of the Due soon list, in display order. */
private sealed interface DueRow {
    val key: String

    data object NothingDated : DueRow { override val key = "nothing-dated" }
    data class Header(val group: DueGroup) : DueRow { override val key = "header-${group.name}" }
    data class Card(val item: DueItem) : DueRow { override val key = "task-${item.id}" }
    data class NoDueDateToggle(val count: Int, val expanded: Boolean) : DueRow { override val key = "no-due-date" }
    data class FinishedToggle(val count: Int, val shown: Boolean) : DueRow { override val key = "show-finished" }
}

/** The Due soon list as rows: dated sections, then "No due date (n)", then "Show finished". */
private fun DueSoonState.rows(): List<DueRow> = buildList {
    if (dated.isEmpty()) add(DueRow.NothingDated)
    dated.forEach { section ->
        add(DueRow.Header(section.group))
        section.tasks.forEach { add(DueRow.Card(it)) }
    }
    if (noDueDate.isNotEmpty()) {
        add(DueRow.NoDueDateToggle(noDueDate.size, noDueDateExpanded))
        if (noDueDateExpanded) noDueDate.forEach { add(DueRow.Card(it)) }
    }
    if (finished.isNotEmpty()) {
        add(DueRow.FinishedToggle(finished.size, showFinished))
        if (showFinished) {
            add(DueRow.Header(DueGroup.FINISHED))
            finished.forEach { add(DueRow.Card(it)) }
        }
    }
}

/**
 * The shaded "Due soon" panel: every course's tasks by date, then the collapsible "No due date"
 * section, then a "Show finished" switch.
 * [fillHeight]: true in the side-by-side layout, where the panel has a fixed height and its list
 * scrolls on its own (lazily); false when stacked, where the whole home screen scrolls.
 */
@Composable
fun DueSoonPanel(
    state: DueSoonState,
    actions: DueSoonActions,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Dimens.PanelPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Due soon", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                RoundIconButton(R.drawable.ic_add, "Add task", actions.onAddTask, Modifier.testTag("due-soon-add"))
            }
            when {
                !state.loaded -> Unit
                state.isEmpty -> EmptyState(
                    title = "Nothing due yet",
                    message = "Tap ＋ to add a task. Assignments from Canvas will show up here too.",
                    modifier = Modifier.fillMaxWidth().then(if (fillHeight) Modifier.weight(1f) else Modifier),
                )
                // One width check for the whole list: narrow panels get compact cards.
                else -> BoxWithConstraints(
                    Modifier.fillMaxWidth().then(if (fillHeight) Modifier.weight(1f) else Modifier).testTag("due-soon-list"),
                ) {
                    val compact = maxWidth < Dimens.TaskCardNarrowWidth
                    val rows = state.rows()
                    if (fillHeight) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(rows, key = { it.key }) { row -> DueRowView(row, state, actions, compact) }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { row -> DueRowView(row, state, actions, compact) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DueRowView(row: DueRow, state: DueSoonState, actions: DueSoonActions, compact: Boolean) {
    when (row) {
        DueRow.NothingDated -> Text(
            "Nothing with a date coming up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        is DueRow.Header -> Text(
            row.group.title,
            style = MaterialTheme.typography.labelLarge,
            color = when (row.group) {
                DueGroup.OVERDUE -> MaterialTheme.colorScheme.error
                DueGroup.FINISHED -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        is DueRow.Card -> TaskCard(
            task = row.item.item.task,
            courseCode = row.item.item.courseCode,
            now = state.now,
            zone = state.zone,
            onClick = { actions.onOpenTask(row.item.id) },
            onStatusChange = { actions.onStatusChange(row.item.id, it) },
            compact = compact,
        )
        is DueRow.NoDueDateToggle -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable(role = Role.Button, onClick = actions.onToggleNoDueDate)
                .padding(vertical = 6.dp)
                .testTag("no-due-date-toggle"),
        ) {
            Text("No due date (${row.count})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Icon(
                painterResource(if (row.expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = if (row.expanded) "Hide tasks with no due date" else "Show tasks with no due date",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        is DueRow.FinishedToggle -> TextButton(onClick = actions.onToggleShowFinished, modifier = Modifier.testTag("show-finished")) {
            Text(if (row.shown) "Hide finished" else "Show finished (${row.count})")
        }
    }
}
