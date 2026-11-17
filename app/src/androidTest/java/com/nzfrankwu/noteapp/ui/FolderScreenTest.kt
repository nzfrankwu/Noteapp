package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.TaskDraft
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.ui.folder.FolderScreen
import com.nzfrankwu.noteapp.ui.folder.FolderViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import com.nzfrankwu.noteapp.data.db.Course
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

    private fun show(folderId: String, width: androidx.compose.ui.unit.Dp = 1120.dp) {
        val viewModel = FolderViewModel(folderId, folders, tasks, NotebookRepository(db))
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = width, height = 800.dp)) {
                    FolderScreen(
                        viewModel, onBack = {}, onOpenTask = {}, onAddTask = {},
                        calendar = { courseId -> Text("calendar for $courseId", Modifier.testTag("test-calendar")) },
                        timeline = { courseId -> Text("timeline for $courseId", Modifier.testTag("test-timeline")) },
                    )
                }
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
    fun linkedFolder_hasTasksCalendarAndTimelineTabs_forItsCourse() {
        val (folderId, courseId) = runBlocking {
            val folderId = (folders.createFolder("EE 204", "ELECTENG 204") as FolderResult.Success).folderId
            folderId to db.courseDao().getAll().single().id
        }
        show(folderId)
        waitFor("No tasks yet")
        compose.onNodeWithTag("folder-tab-tasks").assertIsSelected()

        compose.onNodeWithTag("folder-tab-calendar").performClick()
        waitFor("calendar for $courseId")
        compose.onNodeWithText("No tasks yet").assertDoesNotExist()

        compose.onNodeWithTag("folder-tab-timeline").performClick()
        waitFor("timeline for $courseId")

        compose.onNodeWithTag("folder-tab-tasks").performClick()
        waitFor("No tasks yet")
    }

    @Test
    fun archivingFromTheFolderMenu_marksItArchived_andKeepsItsTasks() {
        val id = runBlocking {
            val folderId = (folders.createFolder("EE 204", "ELECTENG 204") as FolderResult.Success).folderId
            tasks.createTask(TaskDraft("Read chapter 3", db.courseDao().getAll().single().id))
            folderId
        }
        show(id)
        waitFor("Read chapter 3")
        compose.onNodeWithContentDescription("Folder options").performClick()
        compose.onNodeWithText("Archive course").performClick()

        compose.waitUntilAtLeastOneExists(hasTestTag("archived-badge"), 5_000)
        compose.onNodeWithText("Read chapter 3").assertExists()
        assertNotNull(runBlocking { db.courseDao().getAll().single().archivedAt })

        compose.onNodeWithContentDescription("Folder options").performClick()
        compose.onNodeWithText("Unarchive course").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("archived-badge").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun linkACourse_fromTheList() {
        val id = runBlocking {
            db.courseDao().insert(Course("c9", "COMPSYS 201", "Computer Systems", 557, java.time.Instant.EPOCH, java.time.Instant.EPOCH))
            (folders.createFolder("Personal", null) as FolderResult.Success).folderId
        }
        show(id)
        waitFor("Link a course to add tasks here")
        compose.onNodeWithText("Link a course").performClick()
        compose.onNodeWithTag("course-code-field").assertDoesNotExist() // the list comes first
        compose.onNodeWithTag("course-choice").performClick()
        compose.onNodeWithTag("course-choice-COMPSYS 201").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick()

        waitFor("No tasks yet")
        assertEquals("c9", runBlocking { db.folderDao().getById(id)!!.courseId })
    }

    @Test
    fun folderWithoutACourse_hasNoTabs() {
        val id = runBlocking { (folders.createFolder("Personal", null) as FolderResult.Success).folderId }
        show(id)
        waitFor("Link a course to add tasks here")
        compose.onNodeWithTag("folder-tab-calendar").assertDoesNotExist()
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
        compose.onNodeWithText("No notebooks yet").assertExists()
    }

    private fun courseFolder(): String = runBlocking {
        val folderId = (folders.createFolder("EE 204", "ELECTENG 204") as FolderResult.Success).folderId
        tasks.createTask(TaskDraft("Read chapter 3", db.courseDao().getAll().single().id))
        folderId
    }

    @Test
    fun aWideCourseFolder_hasTasksOnTheLeft_andNotesOnTheRight() {
        show(courseFolder())
        waitFor("Read chapter 3")
        val tasksPanel = compose.onNodeWithTag("folder-tasks").fetchSemanticsNode().boundsInRoot
        val notesPanel = compose.onNodeWithTag("folder-content").fetchSemanticsNode().boundsInRoot
        assertEquals(true, tasksPanel.right <= notesPanel.left)
        assertEquals(tasksPanel.top, notesPanel.top, 1f) // both start at the top
        assertEquals(true, notesPanel.width > tasksPanel.width) // the notes get more room
    }

    @Test
    fun aNarrowCourseFolder_isOneList_withTheNotesFirst() {
        show(courseFolder(), width = 600.dp)
        waitFor("Read chapter 3")
        compose.onNodeWithTag("folder-tasks").assertDoesNotExist()
        val notes = compose.onNodeWithText("No notebooks yet").fetchSemanticsNode().boundsInRoot
        val task = compose.onNodeWithText("Read chapter 3").fetchSemanticsNode().boundsInRoot
        assertEquals(true, notes.top < task.top)
    }
}
