package com.nzfrankwu.noteapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.db.CourseListItem
import com.nzfrankwu.noteapp.ui.components.CourseTag
import com.nzfrankwu.noteapp.ui.components.EmptyState
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.theme.Dimens
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings → Courses: every course, current and archived (PHASE-1B.md, B.8). */
class CoursesViewModel(private val folders: FolderRepository) : ViewModel() {
    /** All courses A–Z with their folder (null while loading). */
    val courses: StateFlow<List<CourseListItem>?> = folders.observeCoursesWithFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setColor(courseId: String, color: com.nzfrankwu.noteapp.domain.CourseColorChoice?) {
        viewModelScope.launch { folders.setCourseColor(courseId, color) }
    }

    fun setArchived(courseId: String, archived: Boolean) {
        viewModelScope.launch { folders.setCourseArchived(courseId, archived) }
    }
}

/**
 * Every course in two sections, Current and Archived, each with its folder (or "No folder") and
 * an Archive / Unarchive button. Covers courses without a folder too (e.g. extra Canvas courses).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(viewModel: CoursesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Courses") },
                navigationIcon = { RoundIconButton(R.drawable.ic_arrow_back, "Back", onBack, Modifier.padding(start = 8.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val list = courses ?: return@Box
            val (archived, current) = list.partition { it.isArchived }
            LazyColumn(
                modifier = Modifier.widthIn(max = Dimens.ReadingMaxWidth).fillMaxWidth().testTag("courses-list"),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
            ) {
                item {
                    Text(
                        "Archive last semester's courses to keep Home tidy. An archived course's tasks are hidden " +
                            "from Due soon, Calendar and Timeline and get no reminders. Nothing is deleted, and its " +
                            "folder still shows everything.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (list.isEmpty()) {
                    item { EmptyState("No courses yet", "Courses appear when you link a folder or sync with Canvas.", Modifier.fillMaxWidth()) }
                }
                if (current.isNotEmpty()) {
                    item { SectionTitle("Current") }
                    items(current, key = { it.course.id }) { CourseRow(it, viewModel) }
                }
                if (archived.isNotEmpty()) {
                    item { SectionTitle("Archived (${archived.size})") }
                    items(archived, key = { it.course.id }) { CourseRow(it, viewModel) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun CourseRow(item: CourseListItem, viewModel: CoursesViewModel) {
    val course = item.course
    var choosingColor by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (choosingColor) {
        com.nzfrankwu.noteapp.ui.components.CourseColorDialog(
            code = course.code,
            current = com.nzfrankwu.noteapp.domain.CourseColorChoice.fromKey(course.color),
            onPick = { choosingColor = false; viewModel.setColor(course.id, it) },
            onDismiss = { choosingColor = false },
        )
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Dimens.TilePadding), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CourseTag(course.code, style = MaterialTheme.typography.titleSmall)
                course.name?.takeIf { it.isNotBlank() && it != course.code }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    item.folderName?.let { "Folder: $it" } ?: "No folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.TextButton(
                onClick = { choosingColor = true },
                modifier = Modifier.testTag("color-${course.code}"),
            ) { Text("Colour") }
            if (item.isArchived) {
                FilledTonalButton(
                    onClick = { viewModel.setArchived(course.id, false) },
                    modifier = Modifier.testTag("unarchive-${course.code}"),
                ) { Text("Unarchive") }
            } else {
                OutlinedButton(
                    onClick = { viewModel.setArchived(course.id, true) },
                    modifier = Modifier.testTag("archive-${course.code}"),
                ) { Text("Archive") }
            }
        }
    }
}
