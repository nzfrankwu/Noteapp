package com.nzfrankwu.noteapp.ui.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.DueGroup
import com.nzfrankwu.noteapp.ui.components.CourseChoiceField
import com.nzfrankwu.noteapp.ui.components.CourseTag
import com.nzfrankwu.noteapp.ui.components.EmptyState
import com.nzfrankwu.noteapp.ui.components.InfoBadge
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.components.SegmentedTabs
import com.nzfrankwu.noteapp.ui.components.TaskCard
import com.nzfrankwu.noteapp.ui.home.DeleteFolderDialog
import com.nzfrankwu.noteapp.ui.home.FolderEditDialog
import com.nzfrankwu.noteapp.ui.theme.Dimens
import java.time.Instant

/** Max width of the folder page's content on wide screens. */
private val FolderContentMaxWidth = 900.dp

/** The views of a course folder (PHASE-1B.md, B.7). It always opens on Tasks. */
enum class FolderTab(val label: String, val key: String) {
    TASKS("Tasks", "tasks"),
    CALENDAR("Calendar", "calendar"),
    TIMELINE("Timeline", "timeline"),
}

/**
 * A folder: its subfolders and notebooks in a grid (with an All · Notebooks · Folders filter),
 * then, for a top-level folder with a course, the course's tasks in sections. ＋ adds a notebook
 * or a folder. Subfolders show their top folder's course but no tasks or tabs (PHASE-2.md, 2.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    viewModel: FolderViewModel,
    onBack: () -> Unit,
    onOpenTask: (taskId: String) -> Unit,
    onAddTask: (courseId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens a folder inside this one. */
    onOpenFolder: (folderId: String) -> Unit = {},
    /** Opens a notebook. */
    onOpenNotebook: (notebookId: String) -> Unit = {},
    /** The Calendar tab for this folder's course. */
    calendar: @Composable (courseId: String) -> Unit = {},
    /** The Timeline tab for this folder's course. */
    timeline: @Composable (courseId: String) -> Unit = {},
) {
    val item by viewModel.folder.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val tasksNow by viewModel.tasksNow.collectAsStateWithLifecycle()
    val sections = tasksNow.sections
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val deletedNotebook by viewModel.deletedNotebook.collectAsStateWithLifecycle()
    val coursesWithoutFolder by viewModel.coursesWithoutFolder.collectAsStateWithLifecycle()
    val openNotebook by rememberUpdatedState(onOpenNotebook)
    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(viewModel) { viewModel.openNotebook.collect { openNotebook(it) } }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(deletedNotebook) {
        val gone = deletedNotebook ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Deleted “${gone.title}”", actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteNotebook() else viewModel.dismissDeletedNotebook()
    }

    val isSubfolder = place?.isSubfolder == true
    val courseCode = if (isSubfolder) place?.course?.code else item?.courseCode
    val linkedCourseId = if (isSubfolder) null else item?.folder?.courseId
    var menuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Import PDF: Android's file picker, then the PDF is copied into Noteapp.
    val pickPdf = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            viewModel.importPdf(displayNameOf(resolver, uri)) { resolver.openInputStream(uri) }
        }
    }
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // "Link to task…" from a notebook tile (PHASE-4.md 4.10).
    val linking by viewModel.linking.collectAsStateWithLifecycle()
    val linkChoices by viewModel.linkChoices.collectAsStateWithLifecycle()
    val linkedMessage by viewModel.linked.collectAsStateWithLifecycle()
    LaunchedEffect(linkedMessage) {
        val text = linkedMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.linkedShown()
    }
    if (importing) ImportingDialog()
    var chosenTab by rememberSaveable { mutableStateOf(FolderTab.TASKS) }
    // Without a linked course there's nothing to put on a calendar: Tasks only.
    val tab = if (linkedCourseId == null) FolderTab.TASKS else chosenTab

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == FolderTab.TASKS) {
                Box {
                    FloatingActionButton(onClick = { addMenuOpen = true }, modifier = Modifier.testTag("folder-add")) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = "New notebook or folder")
                    }
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("New notebook") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_note_add), contentDescription = null) },
                            onClick = { addMenuOpen = false; viewModel.startNewNotebook() },
                            modifier = Modifier.testTag("new-notebook"),
                        )
                        DropdownMenuItem(
                            text = { Text("Import PDF") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_picture_as_pdf), contentDescription = null) },
                            onClick = { addMenuOpen = false; pickPdf.launch(arrayOf("application/pdf")) },
                            modifier = Modifier.testTag("import-pdf"),
                        )
                        DropdownMenuItem(
                            text = { Text("New folder") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_create_new_folder), contentDescription = null) },
                            onClick = { addMenuOpen = false; viewModel.startNewSubfolder() },
                            modifier = Modifier.testTag("new-subfolder"),
                        )
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(item?.folder?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val parents = place?.path?.dropLast(1).orEmpty()
                        if (courseCode != null || parents.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (courseCode != null) CourseTag(courseCode, style = MaterialTheme.typography.labelLarge)
                                if (!isSubfolder && item?.isArchived == true) InfoBadge("Archived", Modifier.testTag("archived-badge"))
                                if (parents.isNotEmpty()) {
                                    Text(
                                        "in " + parents.joinToString(" › ") { it.name },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("folder-path"),
                                    )
                                }
                            }
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
                            if (!isSubfolder) {
                                if (courseCode == null) {
                                    DropdownMenuItem(text = { Text("Link a course") }, onClick = { menuOpen = false; viewModel.startLinkCourse() })
                                } else {
                                    DropdownMenuItem(text = { Text("Unlink course") }, onClick = { menuOpen = false; viewModel.startUnlink() })
                                    val archived = item?.isArchived == true
                                    DropdownMenuItem(
                                        text = { Text(if (archived) "Unarchive course" else "Archive course") },
                                        onClick = { menuOpen = false; viewModel.setArchived(!archived) },
                                        modifier = Modifier.testTag("folder-archive"),
                                    )
                                }
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
        BoxWithConstraints(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val gridWidth = min(maxWidth, FolderContentMaxWidth) - Dimens.ScreenPadding * 2
            val columns = libraryColumns(gridWidth)
            val tiles = contents.itemsFor(filter)
            val now = remember(contents) { Instant.now() }
            LazyColumn(
                modifier = Modifier.widthIn(max = FolderContentMaxWidth).fillMaxWidth().testTag("folder-content"),
                // Room at the bottom so the last row isn't hidden under ＋.
                contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = Dimens.ScreenPadding, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
            ) {
                if (contents.loaded && contents.subfolders.isEmpty() && contents.notebooks.isEmpty()) {
                    item(key = "library-empty") {
                        EmptyState(
                            title = "No notebooks yet",
                            message = "Tap ＋ to add a notebook, or a folder to group them (e.g. tutorials, lectures).",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else if (contents.loaded) {
                    item(key = "library-filter") {
                        SegmentedTabs(
                            options = LibraryFilter.entries,
                            selected = filter,
                            label = { it.label },
                            testTag = { "library-filter-${it.key}" },
                            onSelect = viewModel::setFilter,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (tiles.isEmpty()) {
                        item(key = "library-filter-empty") {
                            Text(
                                if (filter == LibraryFilter.FOLDERS) "No folders here." else "No notebooks here.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    tiles.chunked(columns).forEach { row ->
                        item(key = "row-" + row.first().key) {
                            LibraryRow(
                                items = row,
                                columns = columns,
                                now = now,
                                zone = viewModel.zone(),
                                onOpenFolder = onOpenFolder,
                                onOpenNotebook = onOpenNotebook,
                                onRenameFolder = viewModel::startRenameSubfolder,
                                onDeleteFolder = viewModel::startDeleteSubfolder,
                                onRenameNotebook = viewModel::startRenameNotebook,
                                onDeleteNotebook = viewModel::deleteNotebook,
                                courseCode = courseCode,
                                onLinkNotebook = if (viewModel.canLink) viewModel::startLinkNotebook else null,
                            )
                        }
                    }
                }
                if (!isSubfolder) {
                    taskItems(
                        courseCode = courseCode,
                        courseId = linkedCourseId,
                        loaded = item != null,
                        sections = sections,
                        tasksNow = tasksNow,
                        viewModel = viewModel,
                        onAddTask = onAddTask,
                        onOpenTask = onOpenTask,
                    )
                }
            }
        }
    }

    FolderDialogs(dialog, viewModel, item?.folder?.name.orEmpty(), courseCode, coursesWithoutFolder)
    if (linking != null) {
        com.nzfrankwu.noteapp.ui.notebook.LinkTaskSheet(
            choices = linkChoices,
            now = java.time.Instant.now(),
            zone = viewModel.zone(),
            onPick = viewModel::linkNotebookTo,
            onCancel = viewModel::cancelLinkNotebook,
        )
    }
}

/** The Tasks heading and the course's task sections (top-level folders only). */
private fun androidx.compose.foundation.lazy.LazyListScope.taskItems(
    courseCode: String?,
    courseId: String?,
    loaded: Boolean,
    sections: List<com.nzfrankwu.noteapp.domain.TaskSection<com.nzfrankwu.noteapp.data.db.Task>>,
    tasksNow: FolderTasks,
    viewModel: FolderViewModel,
    onAddTask: (String) -> Unit,
    onOpenTask: (String) -> Unit,
) {
    item(key = "tasks-title") {
        Row(Modifier.fillMaxWidth().padding(top = Dimens.PaneGap), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Tasks")
            Spacer(Modifier.weight(1f))
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
        !loaded -> Unit
        courseCode == null -> item(key = "tasks-no-course") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    title = "Link a course to add tasks here",
                    message = "Tasks belong to a course. Link this folder to one, e.g. ELECTENG 204.",
                )
                FilledTonalButton(onClick = viewModel::startLinkCourse) { Text("Link a course") }
            }
        }
        sections.isEmpty() -> item(key = "tasks-empty") {
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

@Composable
private fun FolderDialogs(
    dialog: FolderScreenDialog?,
    viewModel: FolderViewModel,
    folderName: String,
    courseCode: String?,
    coursesWithoutFolder: List<com.nzfrankwu.noteapp.data.db.Course>,
) {
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
                    if (coursesWithoutFolder.isNotEmpty()) {
                        CourseChoiceField(
                            courses = coursesWithoutFolder,
                            selectedCode = d.code,
                            typingOther = d.typingOtherCode,
                            onPick = viewModel::pickCourse,
                            onTypeOther = viewModel::typeOtherCourse,
                            noneLabel = null,
                        )
                    }
                    if (coursesWithoutFolder.isEmpty() || d.typingOtherCode) {
                        OutlinedTextField(
                            value = d.code,
                            onValueChange = viewModel::onTextChange,
                            label = { Text("Course code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth().testTag("course-code-field"),
                        )
                    }
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
        is FolderScreenDialog.ConfirmDelete -> DeleteFolderDialog(
            name = folderName,
            courseCode = courseCode.takeIf { viewModel.place.value?.isSubfolder != true },
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            error = d.error,
        )
        is FolderScreenDialog.NewSubfolder -> FolderEditDialog(
            title = "New folder", name = d.name, onNameChange = viewModel::onTextChange,
            courseCode = "", onCourseCodeChange = null, error = d.error, confirmLabel = "Create",
            onConfirm = viewModel::confirmDialog, onDismiss = viewModel::dismissDialog,
        )
        is FolderScreenDialog.RenameSubfolder -> FolderEditDialog(
            title = "Rename folder", name = d.name, onNameChange = viewModel::onTextChange,
            courseCode = "", onCourseCodeChange = null, error = d.error, confirmLabel = "Save",
            onConfirm = viewModel::confirmDialog, onDismiss = viewModel::dismissDialog,
        )
        is FolderScreenDialog.DeleteSubfolder -> DeleteFolderDialog(
            name = d.name,
            courseCode = null,
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
            error = d.error,
        )
        is FolderScreenDialog.NewNotebook -> NewNotebookDialog(
            title = d.title,
            template = d.template,
            error = d.error,
            onTitleChange = viewModel::onTextChange,
            onPickTemplate = viewModel::pickTemplate,
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
        is FolderScreenDialog.RenameNotebook -> FolderEditDialog(
            title = "Rename notebook", name = d.title, onNameChange = viewModel::onTextChange,
            courseCode = "", onCourseCodeChange = null, error = d.error, confirmLabel = "Save",
            onConfirm = viewModel::confirmDialog, onDismiss = viewModel::dismissDialog,
        )
        null -> Unit
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, modifier = modifier)
}

/** The file's name as the other app gives it (e.g. "Lecture 3.pdf"), or null. */
private fun displayNameOf(resolver: android.content.ContentResolver, uri: android.net.Uri): String? = try {
    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (e: Exception) {
    null
}

/** "Importing PDF…" while a PDF is copied in (a big lecture PDF can take a moment). */
@Composable
private fun ImportingDialog() {
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.testTag("importing-pdf"),
        ) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text("Importing PDF…", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
