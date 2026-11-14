package com.nzfrankwu.noteapp.ui.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.domain.DateText
import com.nzfrankwu.noteapp.domain.TaskDates
import com.nzfrankwu.noteapp.ui.components.DateTimeField
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.theme.Dimens
import java.time.ZoneId

/**
 * New task / Edit task: a single centred form. Your own tasks: name, course, due date, target
 * date, link and notes. Canvas tasks: only the target date and notes (the rest is shown read-only).
 * Closes itself after saving ([onSaved] gets the task's ID).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    viewModel: TaskEditorViewModel,
    onClose: () -> Unit,
    onSaved: (taskId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val form = state.form
    LaunchedEffect(state.savedTaskId) { state.savedTaskId?.let(onSaved) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New task" else "Edit task") },
                navigationIcon = { RoundIconButton(R.drawable.ic_close, "Cancel", onClose, Modifier.padding(start = 8.dp)) },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.loaded && !state.missing && form.canSave,
                        modifier = Modifier.padding(end = 12.dp).testTag("save-task"),
                    ) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            if (!state.loaded) return@Box
            Column(
                modifier = Modifier
                    .widthIn(max = Dimens.ReadingMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap),
            ) {
                if (state.missing) {
                    Text("This task no longer exists.", color = MaterialTheme.colorScheme.error)
                    return@Column
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("form-error")) }

                if (form.isCanvas) {
                    CanvasSummary(form, courses)
                } else {
                    TitleField(form.title, viewModel::onTitleChange, autoFocus = viewModel.isNew && form.title.isEmpty())
                    CoursePicker(courses, form.courseId, viewModel::onCourseChange)
                    DateTimeField(
                        label = "Due date",
                        hint = "Optional. Without a time it's due at 11:59 pm.",
                        date = form.dueDate,
                        time = form.dueTime,
                        defaultTime = TaskDates.DefaultDueTime,
                        onDateChange = viewModel::onDueDateChange,
                        onTimeChange = viewModel::onDueTimeChange,
                        tag = "due",
                    )
                }
                DateTimeField(
                    label = "My target date",
                    hint = "Optional: when you want it done. Used for sorting and reminders when set. " +
                        "Without a time it's 9:00 am.",
                    date = form.targetDate,
                    time = form.targetTime,
                    defaultTime = TaskDates.DefaultTargetTime,
                    onDateChange = viewModel::onTargetDateChange,
                    onTimeChange = viewModel::onTargetTimeChange,
                    tag = "target",
                )
                if (!form.isCanvas) {
                    OutlinedTextField(
                        value = form.url,
                        onValueChange = viewModel::onUrlChange,
                        label = { Text("Link (optional)") },
                        placeholder = { Text("e.g. a course page") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth().testTag("link-field"),
                    )
                }
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("Notes (optional)") },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().testTag("notes-field"),
                )
            }
        }
    }
}

@Composable
private fun TitleField(title: String, onChange: (String) -> Unit, autoFocus: Boolean) {
    val focus = remember { FocusRequester() }
    OutlinedTextField(
        value = title,
        onValueChange = onChange,
        label = { Text("Task name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("title-field"),
    )
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
}

/** A read-only course field that opens a list of courses. */
@Composable
private fun CoursePicker(courses: List<Course>?, selectedId: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = courses?.firstOrNull { it.id == selectedId }
    if (courses != null && courses.isEmpty()) {
        Text(
            "No courses yet. On the home screen, make a folder with a course code (e.g. ELECTENG 204) first.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let(::courseLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Course") },
            placeholder = { Text("Pick a course") },
            trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field is read-only; this transparent layer makes the whole field open the list.
        Box(Modifier.matchParentSize().clickable { open = true }.testTag("course-field"))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            courses.orEmpty().forEach { course ->
                DropdownMenuItem(
                    text = { Text(courseLabel(course)) },
                    onClick = { onSelect(course.id); open = false },
                )
            }
        }
    }
}

private fun courseLabel(course: Course): String =
    if (course.name.isNullOrBlank()) course.code else "${course.code} · ${course.name}"

/** For Canvas tasks: what comes from Canvas, read-only. */
@Composable
private fun CanvasSummary(form: TaskForm, courses: List<Course>?) {
    val code = courses?.firstOrNull { it.id == form.courseId }?.code ?: "No course"
    val zone = ZoneId.systemDefault()
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.TilePadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(form.title, style = MaterialTheme.typography.titleMedium)
            Text(code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                form.dueAt(zone)?.let { "Due ${DateText.dateTime(it, zone)}" } ?: "No due date",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "From Canvas: the name, course, due date and link stay in sync with Canvas. " +
                    "You can set your own target date and notes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
