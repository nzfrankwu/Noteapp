package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
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
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import com.nzfrankwu.noteapp.ui.timeline.TimelineScreen
import com.nzfrankwu.noteapp.ui.timeline.TimelineViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The timeline with a real in-memory database. The clock is fixed at Sun 15 Nov 2026; tasks added
 * then start their bars on 15 Nov.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TimelineScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Pacific/Auckland")
    private val clock = Clock.fixed(LocalDateTime.parse("2026-11-15T10:00").atZone(zone).toInstant(), zone)
    private var opened: String? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val folders = FolderRepository(db)
        val tasks = TaskRepository(db, clock)
        runBlocking {
            folders.createFolder("Electromagnetics", "ELECTENG 204")
            val course = db.courseDao().getAll().single().id
            fun due(month: Int, day: Int) = LocalDateTime.of(2026, month, day, 23, 59).atZone(zone).toInstant()
            tasks.createTask(TaskDraft("Assignment 3", course, dueAt = due(11, 20)))
            // Added on 1 Dec, so its bar starts after the first two weeks shown (9–22 Nov).
            val december = Clock.fixed(LocalDateTime.parse("2026-12-01T09:00").atZone(zone).toInstant(), zone)
            TaskRepository(db, december).createTask(TaskDraft("Exam revision", course, dueAt = due(12, 30)))
            tasks.createTask(TaskDraft("Read ahead", course))
        }
        val viewModel = TimelineViewModel(tasks, clock, zone)
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 740.dp)) {
                    TimelineScreen(viewModel, onOpenTask = { opened = it })
                }
            }
        }
        compose.waitUntilAtLeastOneExists(hasTestTag("timeline-bar"), 5_000)
    }

    @Test
    fun visibleTask_isABar_thatOpensTheTask() {
        compose.onNodeWithTag("timeline-bar").performClick()
        compose.waitUntil(5_000) { opened != null }
        compose.onNodeWithTag("timeline-now").assertExists()
    }

    @Test
    fun undatedTasks_areCounted_notShown() {
        compose.onNodeWithTag("timeline-undated").assertExists()
        compose.onNodeWithText("1 task without a due date").assertExists()
        compose.onNodeWithText("Read ahead").assertDoesNotExist()
    }

    @Test
    fun laterTask_isAnArrow_andTappingItMovesThere() {
        compose.onNodeWithTag("timeline-arrow-right").performClick()
        // Now the exam's bar is in view, and Assignment 3 has become an arrow on the left.
        compose.waitUntilAtLeastOneExists(hasContentDescription("Exam revision", substring = true), 5_000)
        compose.onNodeWithTag("timeline-arrow-left").assertExists()
    }

    @Test
    fun switchingZoomBackAndForth_keepsTodayInView() {
        compose.onNodeWithTag("timeline-zoom-MONTH").performClick()
        compose.onNodeWithTag("timeline-zoom-TWO_WEEKS").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("timeline-bar").fetchSemanticsNodes().size == 1 }
        assertEquals(1, compose.onAllNodesWithTag("timeline-arrow-right").fetchSemanticsNodes().size)
    }
}
