package com.nzfrankwu.noteapp.ui.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.CanvasSettings
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.FolderListItem
import com.nzfrankwu.noteapp.ui.components.CourseTag
import com.nzfrankwu.noteapp.ui.components.EmptyState
import com.nzfrankwu.noteapp.ui.message
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.theme.Dimens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Link your Canvas courses": give each imported course without a folder a folder (IDEA.md §2.1). */
class LinkCoursesViewModel(
    private val folders: FolderRepository,
    private val settings: CanvasSettings,
) : ViewModel() {
    /** Canvas courses with no folder yet (null while loading). */
    val courses: StateFlow<List<Course>?> = folders.observeCanvasCoursesWithoutFolder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Folders that don't have a course yet: a Canvas course can be added to one of these. */
    val freeFolders: StateFlow<List<FolderListItem>> = folders.observeFolders()
        .map { list -> list.filter { it.courseCode == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun createFolder(course: Course) {
        viewModelScope.launch { report(folders.createFolderForCourse(folderNameFor(course), course.id)) }
    }

    fun addToFolder(course: Course, folderId: String) {
        viewModelScope.launch { report(folders.linkCourseById(folderId, course.id)) }
    }

    /** "Not now": the home screen stops suggesting this course. It stays listed here. */
    fun notNow(course: Course) {
        viewModelScope.launch {
            settings.hideLinkPrompts(listOf(course.id))
            _message.value = "${course.code} won't be suggested again. Its tasks still show in Due soon."
        }
    }

    private fun report(result: FolderResult) {
        _message.value = when (result) {
            is FolderResult.Success -> null
            is FolderResult.Failure -> result.error.message()
        }
    }

    companion object {
        /** A new folder is named after the course: "Engineering Electromagnetics", or the code if Canvas gave no name. */
        fun folderNameFor(course: Course): String = course.name?.takeIf { it.isNotBlank() } ?: course.code
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkCoursesScreen(viewModel: LinkCoursesViewModel, onBack: () -> Unit) {
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val freeFolders by viewModel.freeFolders.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Link your Canvas courses") },
                navigationIcon = { RoundIconButton(R.drawable.ic_arrow_back, "Back", onBack, Modifier.padding(start = 8.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = Dimens.ReadingMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.ItemGap),
            ) {
                item {
                    Text(
                        "Give each course a folder, so its tasks (and, later, its notebooks) live together. " +
                            "Its tasks show in Due soon either way.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("link-message")) } }
                val list = courses
                when {
                    list == null -> Unit
                    list.isEmpty() -> item {
                        EmptyState("All set", "Every Canvas course has a folder.", Modifier.fillMaxWidth())
                    }
                    else -> items(list, key = { it.id }) { course ->
                        CourseRow(course, freeFolders, viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseRow(course: Course, freeFolders: List<FolderListItem>, viewModel: LinkCoursesViewModel) {
    var pickFolder by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.TilePadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CourseTag(course.code, style = MaterialTheme.typography.titleMedium)
            course.name?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { viewModel.createFolder(course) }, modifier = Modifier.testTag("create-folder-${course.code}")) {
                    Text("Create folder “${LinkCoursesViewModel.folderNameFor(course)}”")
                }
                if (freeFolders.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { pickFolder = true }) { Text("Add to a folder") }
                        DropdownMenu(expanded = pickFolder, onDismissRequest = { pickFolder = false }) {
                            freeFolders.forEach { folder ->
                                DropdownMenuItem(
                                    text = { Text(folder.folder.name) },
                                    onClick = { pickFolder = false; viewModel.addToFolder(course, folder.folder.id) },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { viewModel.notNow(course) }) { Text("Not now") }
            }
        }
    }
}
