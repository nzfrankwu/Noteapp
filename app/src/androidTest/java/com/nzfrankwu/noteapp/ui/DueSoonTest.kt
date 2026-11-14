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
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.TaskDraft
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.TaskResult
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.TaskStatus
import com.nzfrankwu.noteapp.ui.home.HomeScreen
import com.nzfrankwu.noteapp.ui.home.HomeViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.temporal.ChronoUnit

/** The home screen's Due soon panel with a real (in-memory) database. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DueSoonTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: NoteAppDatabase
    private lateinit var tasks: TaskRepository
    private var opened: String? = null
    private var addPressed = false

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val folders = FolderRepository(db)
        tasks = TaskRepository(db)
        runBlocking {
            folders.createFolder("Electromagnetics", "ELECTENG 204")
            folders.createFolder("Circuits", "ELECTENG 292")
            val courses = db.courseDao().getAll().associateBy { it.code }
            val now = Instant.now()
            fun add(title: String, code: String, dueInDays: Long?) = runBlocking {
                (tasks.createTask(TaskDraft(title, courses.getValue(code).id, dueAt = dueInDays?.let { now.plus(it, ChronoUnit.DAYS) })) as TaskResult.Success).taskId
            }
            add("Late lab", "ELECTENG 292", -1)
            add("Assignment 3", "ELECTENG 204", 10)
            add("Read chapter 4", "ELECTENG 204", null)
            val done = add("Quiz 1", "ELECTENG 204", -3)
            tasks.setStatus(done, TaskStatus.DONE)
        }
        val viewModel = HomeViewModel(folders, tasks)
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 800.dp)) {
                    HomeScreen(viewModel, onOpenSettings = {}, onOpenFolder = {}, onOpenTask = { opened = it }, onAddTask = { addPressed = true })
                }
            }
        }
    }

    // The database is not closed after each test: screens may still be reading it when the
    // test ends, and closing it then crashes the run. In-memory databases are freed anyway.

    private fun waitFor(text: String) = compose.waitUntilAtLeastOneExists(hasText(text), 5_000)

    @Test
    fun tasksFromEveryCourse_showInSections_withTheirCourse() {
        waitFor("Late lab")
        compose.onAllNodesWithText("Overdue").onFirst().assertExists()
        compose.onNodeWithText("Assignment 3").assertExists()
        compose.onAllNodesWithText("Later").onFirst().assertExists()
        compose.onAllNodesWithText("ELECTENG 292").onFirst().assertExists() // also on its folder tile
    }

    @Test
    fun noDueDate_isCollapsedUntilTapped() {
        waitFor("No due date (1)")
        compose.onNodeWithText("Read chapter 4").assertDoesNotExist()
        compose.onNodeWithTag("no-due-date-toggle").performClick()
        waitFor("Read chapter 4")
    }

    @Test
    fun finishedTasks_areHiddenBehindShowFinished() {
        waitFor("Show finished (1)")
        compose.onNodeWithText("Quiz 1").assertDoesNotExist()
        compose.onNodeWithTag("show-finished").performClick()
        waitFor("Quiz 1")
        waitFor("Hide finished")
    }

    @Test
    fun tappingACard_opensIt_andPlusAddsATask() {
        waitFor("Assignment 3")
        compose.onNodeWithText("Assignment 3").performClick()
        compose.waitUntil(5_000) { opened != null }
        compose.onNodeWithTag("due-soon-add").performClick()
        assertEquals(true, addPressed)
    }
}
