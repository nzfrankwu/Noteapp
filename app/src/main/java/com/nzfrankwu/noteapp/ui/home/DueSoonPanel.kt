package com.nzfrankwu.noteapp.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * The shaded "Due soon" panel: every course's tasks by date, then the collapsible "No due date"
 * section, then a "Show finished" switch.
 * [fillHeight]: true in the side-by-side layout, where the panel has a fixed height and scrolls
 * on its own; false when stacked, where the whole home screen scrolls.
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
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fillHeight) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier)
                        .testTag("due-soon-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { DueSoonItems(state, actions) }
            }
        }
    }
}

@Composable
private fun ColumnScope.DueSoonItems(state: DueSoonState, actions: DueSoonActions) {
    if (state.dated.isEmpty()) {
        Text(
            "Nothing with a date coming up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    state.dated.forEach { section ->
        GroupTitle(
            section.group.title,
            color = if (section.group == DueGroup.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        section.tasks.forEach { Card(it, state, actions) }
    }

    if (state.noDueDate.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable(role = Role.Button, onClick = actions.onToggleNoDueDate)
                .padding(vertical = 6.dp)
                .testTag("no-due-date-toggle"),
        ) {
            Text(
                "No due date (${state.noDueDate.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                painterResource(if (state.noDueDateExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = if (state.noDueDateExpanded) "Hide tasks with no due date" else "Show tasks with no due date",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.noDueDateExpanded) state.noDueDate.forEach { Card(it, state, actions) }
    }

    if (state.finished.isNotEmpty()) {
        TextButton(onClick = actions.onToggleShowFinished, modifier = Modifier.testTag("show-finished")) {
            Text(if (state.showFinished) "Hide finished" else "Show finished (${state.finished.size})")
        }
        if (state.showFinished) {
            GroupTitle(DueGroup.FINISHED.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.finished.forEach { Card(it, state, actions) }
        }
    }
}

@Composable
private fun GroupTitle(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Card(item: DueItem, state: DueSoonState, actions: DueSoonActions) {
    TaskCard(
        task = item.item.task,
        courseCode = item.item.courseCode,
        now = state.now,
        zone = state.zone,
        onClick = { actions.onOpenTask(item.id) },
        onStatusChange = { actions.onStatusChange(item.id, it) },
    )
}
