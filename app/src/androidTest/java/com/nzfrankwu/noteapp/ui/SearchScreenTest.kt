package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.NotebookResult
import com.nzfrankwu.noteapp.data.TaskDraft
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.TaskResult
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.ui.search.SearchScreen
import com.nzfrankwu.noteapp.ui.search.SearchViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** PHASE-7.md 7.8: search tasks, notebooks and folders. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: NoteAppDatabase
    private var openedTask: String? = null
    private var openedNotebook: String? = null
    private var openedFolder: String? = null
    private lateinit var taskId: String
    private lateinit var notebookId: String
    private lateinit var folderId: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val folders = FolderRepository(db)
        val tasks = TaskRepository(db)
        val notebooks = NotebookRepository(db)
        runBlocking {
            folderId = (folders.createFolder("Electromagnetics", "ELECTENG 204") as FolderResult.Success).folderId
            val courseId = db.courseDao().getAll().single().id
            taskId = (tasks.createTask(TaskDraft("Assignment 3", courseId, notes = "Smith chart questions")) as TaskResult.Success).taskId
            tasks.createTask(TaskDraft("Quiz 4", courseId))
            notebookId = (notebooks.createNotebook(folderId, "Smith chart notes") as NotebookResult.Success).id
        }
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1100.dp, height = 800.dp)) {
                    SearchScreen(
                        SearchViewModel(tasks, notebooks, folders),
                        onBack = {},
                        onOpenTask = { openedTask = it },
                        onOpenNotebook = { openedNotebook = it },
                        onOpenFolder = { openedFolder = it },
                    )
                }
            }
        }
    }

    @Test
    fun startsWithAHint_thenFindsTasksAndNotebooks_byNotesAndTitles() {
        compose.onNodeWithTag("search-hint").assertExists()
        compose.onNodeWithTag("search-field").performTextInput("smith")
        compose.waitUntilAtLeastOneExists(hasText("Tasks (1)"), 5_000)
        compose.onNodeWithText("Notebooks (1)").assertExists()
        compose.onNodeWithText("Quiz 4").assertDoesNotExist()

        compose.onNodeWithText("Assignment 3").performClick()
        assertEquals(taskId, openedTask)
        compose.onNodeWithTag("search-notebook-Smith chart notes").performClick()
        assertEquals(notebookId, openedNotebook)
    }

    @Test
    fun findsFoldersByCourseCode_andSaysWhenNothingMatches() {
        compose.onNodeWithTag("search-field").performTextInput("electeng 204")
        compose.waitUntilAtLeastOneExists(hasText("Folders & courses (1)"), 5_000)
        compose.onNodeWithTag("search-folder-Electromagnetics").performClick()
        assertEquals(folderId, openedFolder)

        compose.onNodeWithTag("search-field").performTextReplacement("fourier")
        compose.waitUntilAtLeastOneExists(hasText("Nothing matches “fourier”."), 5_000)
        compose.onNodeWithTag("search-clear").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("search-hint"), 5_000)
    }
}
