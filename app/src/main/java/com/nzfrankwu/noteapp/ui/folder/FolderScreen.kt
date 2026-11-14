package com.nzfrankwu.noteapp.ui.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.ui.components.CourseTag
import com.nzfrankwu.noteapp.ui.components.EmptyState
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.components.SegmentedTabs
import com.nzfrankwu.noteapp.ui.components.TaskCard
import com.nzfrankwu.noteapp.domain.DueGroup
import com.nzfrankwu.noteapp.ui.home.FolderEditDialog
import com.nzfrankwu.noteapp.ui.theme.Dimens

/** Max width of the folder page's content on wide screens. */
private val FolderContentMaxWidth = 900.dp

/** The views of a course folder (PHASE-1B.md, B.7). It always opens on Tasks. */
enum class FolderTab(val label: String, val key: String) {
    TASKS("Tasks", "tasks"),
    CALENDAR("Calendar", "calendar"),
    TIMELINE("Timeline", "timeline"),
}

/**
 * A folder: its course, a Notebooks area (Phase 2), and the course's tasks in sections.
 * "Add task" (when a course is linked) opens the task form with the course filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    viewModel: FolderViewModel,
    onBack: () -> Unit,
    onOpenTask: (taskId: String) -> Unit,
    onAddTask: (courseId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** The Calendar tab for this folder's course. */
    calendar: @Composable (courseId: String) -> Unit = {},
    /** The Timeline tab for this folder's course. */
    timeline: @Composable (courseId: String) -> Unit = {},
) {
    val item by viewModel.folder.collectAsStateWithLifecycle()
    val tasksNow by viewModel.tasksNow.collectAsStateWithLifecycle()
    val sections = tasksNow.sections
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { if (deleted) onBack() }

    val courseCode = item?.courseCode
    val linkedCourseId = item?.folder?.courseId
    var menuOpen by remember { mutableStateOf(false) }
    var chosenTab by rememberSaveable { mutableStateOf(FolderTab.TASKS) }
    // Without a linked course there's nothing to put on a calendar: Tasks only.
    val tab = if (linkedCourseId == null) FolderTab.TASKS else chosenTab

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(item?.folder?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (courseCode != null) {
                            CourseTag(courseCode, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                navigationIcon = {
                    RoundIconButton(R.drawable.ic_arrow_back, "Back", onBack, Modifier.padding(start = 8.dp))
                },
                actions = {
                    if (linkedCourseId != null) {
                        SegmentedTabs(
                            options = FolderTab.entries,
                            selected = tab,
                            label = { it.label },
                            testTag = { "folder-tab-${it.key}" },
                            onSelect = { chosenTab = it },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    Box {
                        RoundIconButton(R.drawable.ic_more_vert, "Folder options", { menuOpen = true }, Modifier.padding(end = 8.dp))
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename folder") }, onClick = { menuOpen = false; viewModel.startRename() })
                            if (courseCode == null) {
                                DropdownMenuItem(text = { Text("Link a course") }, onClick = { menuOpen = false; viewModel.startLinkCourse() })
                            } else {
                                DropdownMenuItem(text = { Text("Unlink course") }, onClick = { menuOpen = false; viewModel.startUnlink() })
                            }
                            DropdownMenuItem(
                                text = { Text("Delete folder", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; viewModel.startDelete() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        if (tab != FolderTab.TASKS && linkedCourseId != null) {
            Box(Modifier.padding(innerPadding).fillMaxSize()) {
                if (tab == FolderTab.CALENDAR) calendar(linkedCourseId) else timeline(linkedCourseId)
            }
            return@Scaffold
        }
        Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = FolderContentMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
            ) {
                item { SectionTitle("Notebooks") }
                item {
                    EmptyState(
                        title = "Notebooks arrive in Phase 2",
                        message = "Handwritten notebooks and PDFs for this folder will live here.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = Dimens.PaneGap), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Tasks")
                        Spacer(Modifier.weight(1f))
                        val courseId = item?.folder?.courseId
                        if (courseId != null) {
                            FilledTonalButton(
                                onClick = { onAddTask(courseId) },
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                modifier = Modifier.testTag("add-task"),
                            ) {
                                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Add task")
                            }
                        }
                    }
                }
                when {
                    item == null -> Unit
                    courseCode == null -> item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                title = "Link a course to add tasks here",
                                message = "Tasks belong to a course. Link this folder to one, e.g. ELECTENG 204.",
                            )
                            FilledTonalButton(onClick = viewModel::startLinkCourse) { Text("Link a course") }
                        }
                    }
                    sections.isEmpty() -> item {
                        EmptyState(
                            title = "No tasks yet",
                            message = "Tap “Add task” to add your own. Canvas assignments for $courseCode will show up here too.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> sections.forEach { section ->
                        item(key = "header-${section.group}") {
                            Text(
                                text = section.group.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (section.group == DueGroup.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(section.tasks, key = { it.id }) { task ->
                            TaskCard(
                                task = task,
                                courseCode = courseCode,
                                now = tasksNow.now,
                                zone = viewModel.zone(),
                                onClick = { onOpenTask(task.id) },
                                onStatusChange = { viewModel.setStatus(task.id, it) },
                                showCourse = false,
                            )
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is FolderScreenDialog.Rename -> FolderEditDialog(
            title = "Rename folder", name = d.name, onNameChange = viewModel::onTextChange,
            courseCode = "", onCourseCodeChange = null, error = d.error, confirmLabel = "Save",
            onConfirm = viewModel::confirmDialog, onDismiss = viewModel::dismissDialog,
        )
        is FolderScreenDialog.LinkCourse -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Link a course") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = d.code,
                        onValueChange = viewModel::onTextChange,
                        label = { Text("Course code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth().testTag("course-code-field"),
                    )
                    if (d.error != null) Text(d.error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDialog, enabled = d.code.isNotBlank(), modifier = Modifier.testTag("dialog-confirm")) {
                    Text("Link")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } },
        )
        FolderScreenDialog.ConfirmUnlink -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Unlink $courseCode?") },
            text = { Text("The course and its tasks are kept (they still show in Due soon). You can link it again later.") },
            confirmButton = { TextButton(onClick = viewModel::confirmDialog, modifier = Modifier.testTag("dialog-confirm")) { Text("Unlink") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } },
        )
        FolderScreenDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Delete “${item?.folder?.name.orEmpty()}”?") },
            text = { Text(if (courseCode != null) "Only the folder is removed. Tasks for $courseCode are kept." else "Only the folder is removed.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmDialog,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("dialog-confirm"),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") } },
        )
        null -> Unit
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}
