package com.nzfrankwu.noteapp.ui.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.DateText
import com.nzfrankwu.noteapp.domain.Links
import com.nzfrankwu.noteapp.domain.TaskRules
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.components.CourseTag
import com.nzfrankwu.noteapp.ui.components.DaysLeftText
import com.nzfrankwu.noteapp.ui.components.InfoBadge
import com.nzfrankwu.noteapp.ui.components.OverdueBadge
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.components.StatusChip
import com.nzfrankwu.noteapp.ui.components.openLink
import com.nzfrankwu.noteapp.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId

/**
 * One task: name, course, status (tap to change), dates, link, Canvas description, your notes,
 * and (Phase 2) linked notebooks. Edit and Delete are in the top bar. Deleting closes the
 * screen; [onDeleted] gets the deleted task so the app can offer Undo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    viewModel: TaskDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { deleted?.let(onDeleted) }

    val shown = state as? TaskDetailState.Shown
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { RoundIconButton(R.drawable.ic_arrow_back, "Back", onBack, Modifier.padding(start = 8.dp)) },
                actions = {
                    if (shown != null) {
                        RoundIconButton(R.drawable.ic_edit, "Edit task", onEdit, Modifier.padding(end = 8.dp))
                        if (TaskDetailViewModel.canDelete(shown.item.task)) {
                            RoundIconButton(R.drawable.ic_delete, "Delete task", viewModel::delete, Modifier.padding(end = 8.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                TaskDetailState.Loading -> Unit
                TaskDetailState.Gone -> Text(
                    "This task was deleted.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Dimens.ScreenPadding),
                )
                is TaskDetailState.Shown -> TaskDetails(s.item, viewModel.now(), viewModel.zone(), viewModel::setStatus)
            }
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(it) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskDetails(item: TaskWithCourse, now: Instant, zone: ZoneId, onStatusChange: (TaskStatus) -> Unit) {
    val task = item.task
    val context = LocalContext.current
    val isCanvas = task.source == TaskSource.CANVAS
    Column(
        modifier = Modifier
            .widthIn(max = Dimens.ReadingMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CourseTag(item.courseCode, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = " · " + if (isCanvas) "Canvas assignment" else "Your task",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SelectionContainer {
                Text(task.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("task-title"))
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(task.status, onStatusChange)
            if (TaskRules.isOverdue(task, now)) OverdueBadge()
            if (TaskRules.isPastTarget(task, now)) InfoBadge("Past target")
            if (task.removedFromCanvas) InfoBadge("Removed from Canvas")
        }

        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Dimens.TilePadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Due", task.dueAt?.let { DateText.dateTime(it, zone, now) } ?: "No due date") {
                    DaysLeftText(task, now, zone, style = MaterialTheme.typography.bodyMedium)
                }
                InfoRow("My target date", task.targetAt?.let { DateText.dateTime(it, zone, now) } ?: "Not set")
                if (isCanvas) InfoRow("On Canvas", submissionText(task, zone, now))
            }
        }

        if (task.url != null) {
            FilledTonalButton(
                onClick = { openLink(context, task.url) },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.testTag("open-link"),
            ) {
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(if (isCanvas) "Open in Canvas" else Links.host(task.url)?.let { "Open $it" } ?: "Open link")
            }
        }

        if (!task.descriptionHtml.isNullOrBlank()) {
            Section("Description") { HtmlText(task.descriptionHtml, baseUrl = task.url) }
        }

        Section("My notes") {
            if (task.notes.isNullOrBlank()) {
                Text("No notes yet. Tap Edit to add some.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SelectionContainer { Text(task.notes, style = MaterialTheme.typography.bodyLarge) }
            }
        }

        Section("Linked notebooks") {
            Text("Linking notebooks to tasks arrives in Phase 2.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, trailing: (@Composable () -> Unit)? = null) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

private fun submissionText(task: Task, zone: ZoneId, now: Instant): String {
    val submittedAt = task.canvasSubmittedAt
    return when {
        submittedAt != null -> "Submitted ${DateText.dateTime(submittedAt, zone, now)}"
        task.canvasSubmissionState == "graded" -> "Graded"
        else -> "Not submitted yet"
    }
}

/**
 * Canvas's description, turned from HTML into formatted text. Links open in the browser, but
 * only web links: relative Canvas links are completed with the Canvas site, anything else is refused.
 */
@Composable
private fun HtmlText(html: String, baseUrl: String?) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(html, linkColor, baseUrl) {
        AnnotatedString.fromHtml(
            htmlString = html,
            linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            linkInteractionListener = { link ->
                (link as? LinkAnnotation.Url)?.let { openLink(context, Links.resolve(baseUrl, it.url)) }
            },
        )
    }
    SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
}
