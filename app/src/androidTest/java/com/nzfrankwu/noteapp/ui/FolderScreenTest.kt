package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.FolderResult
import com.nzfrankwu.noteapp.data.TaskDraft
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.ui.folder.FolderScreen
import com.nzfrankwu.noteapp.ui.folder.FolderViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The folder screen with a real (in-memory) database. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FolderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: NoteAppDatabase
    private lateinit var folders: FolderRepository
    private lateinit var tasks: TaskRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        folders = FolderRepository(db)
        tasks = TaskRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun show(folderId: String) {
        val viewModel = FolderViewModel(folderId, folders, tasks)
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 800.dp)) { FolderScreen(viewModel, onBack = {}) }
            }
        }
    }

    private fun waitFor(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), 5_000)

    @Test
    fun unlinkedFolder_offersToLinkACourse_andLinkingWorks() {
        val id = runBlocking { (folders.createFolder("Personal", null) as FolderResult.Success).folderId }
        show(id)
        waitFor("Link a course to add tasks here")

        compose.onNodeWithText("Link a course").performClick()
        compose.onNodeWithTag("course-code-field").performTextInput("electeng 204")
        compose.onNodeWithTag("dialog-confirm").performClick()

        waitFor("ELECTENG 204")
        waitFor("No tasks yet")
    }

    @Test
    fun linkedFolder_showsItsTasksInSections() {
        val id = runBlocking {
            val folderId = (folders.createFolder("EE 204", "ELECTENG 204") as FolderResult.Success).folderId
            val courseId = db.courseDao().getAll().single().id
            tasks.createTask(TaskDraft("Read chapter 3", courseId)) // no due date
            folderId
        }
        show(id)
        waitFor("Read chapter 3")
        // "No due date" appears as the section heading and on the task's own row.
        compose.onAllNodesWithText("No due date").onFirst().assertExists()
        compose.onNodeWithText("Notebooks arrive in Phase 2").assertExists()
    }
}
