package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.ui.calendar.CalendarScreen
import com.nzfrankwu.noteapp.ui.calendar.CalendarViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The month calendar with a real in-memory database and a fixed "now" (Sun 15 Nov 2026). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Pacific/Auckland")
    private val clock = Clock.fixed(LocalDateTime.parse("2026-11-15T10:00").atZone(zone).toInstant(), zone)
    private var opened: String? = null
    private var addedOn: LocalDate? = null
    private lateinit var tasks: TaskRepository
    private lateinit var courses: Map<String, String>

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val folders = FolderRepository(db)
        tasks = TaskRepository(db)
        runBlocking {
            folders.createFolder("Electromagnetics", "ELECTENG 204")
            folders.createFolder("Computers", "COMPSYS 201")
            courses = db.courseDao().getAll().associate { it.code to it.id }
            fun due(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).atZone(zone).toInstant()
            // Five tasks on Fri 20 Nov: more than fit, so "+N more" shows.
            for (i in 1..5) tasks.createTask(TaskDraft("Task $i", courses.getValue("ELECTENG 204"), dueAt = due(20, 8 + i)))
            tasks.createTask(TaskDraft("Quiz 4", courses.getValue("COMPSYS 201"), dueAt = due(16, 23)))
        }
    }

    private fun show(courseId: String? = null) {
        val viewModel = CalendarViewModel(tasks, clock, zone, courseId = courseId)
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 740.dp)) {
                    CalendarScreen(viewModel, onOpenTask = { opened = it }, onAddTaskOnDay = { addedOn = it })
                }
            }
        }
    }

    private fun showAll() {
        show()
        compose.waitUntilAtLeastOneExists(hasText("Quiz 4"), 5_000)
    }

    @Test
    fun insideACourseFolder_onlyThatCourse_andNoCourseFilter() {
        show(courseId = courses.getValue("COMPSYS 201"))
        compose.waitUntilAtLeastOneExists(hasText("Quiz 4"), 5_000)
        compose.onNodeWithTag("calendar-more-2026-11-20").assertDoesNotExist()
        compose.onNodeWithText("Task 1").assertDoesNotExist()
        compose.onNodeWithTag("course-filter").assertDoesNotExist()
    }

    @Test
    fun showsTheMonth_andTappingAChipOpensTheTask() {
        showAll()
        compose.onNodeWithTag("calendar-month").assertTextEquals("November 2026")
        compose.onNodeWithText("Quiz 4").performClick()
        compose.waitUntil(5_000) { opened != null }
    }

    @Test
    fun aFullDay_showsMore_andTheDaySheetListsEverything() {
        showAll()
        compose.onNodeWithTag("calendar-more-2026-11-20").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("day-sheet"), 5_000)
        compose.onNodeWithText("Fri 20 Nov").assertExists()
        assertEquals(5, compose.onAllNodesWithTag("task-card").fetchSemanticsNodes().size)

        compose.onNodeWithTag("add-task-on-day").performClick()
        assertEquals(LocalDate.of(2026, 11, 20), addedOn)
    }

    @Test
    fun arrowsAndToday_changeTheMonth() {
        showAll()
        compose.onNodeWithTag("calendar-next").performClick()
        compose.onNodeWithTag("calendar-month").assertTextEquals("December 2026")
        compose.onNodeWithText("Quiz 4").assertDoesNotExist()
        compose.onNodeWithTag("calendar-today").performClick()
        compose.onNodeWithTag("calendar-month").assertTextEquals("November 2026")
    }

    @Test
    fun courseFilter_narrowsToOneCourse() {
        showAll()
        compose.onNodeWithTag("course-filter").performClick()
        compose.onNodeWithTag("course-filter-COMPSYS 201").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("calendar-more-2026-11-20").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Quiz 4").assertExists()
    }
}
