package com.nzfrankwu.noteapp.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.CanvasSettings
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.ui.home.HomeScreen
import com.nzfrankwu.noteapp.ui.home.HomeViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/** Home's "isn't in your Canvas courses any more. Archive it?" strip (made-up courses). */
@RunWith(AndroidJUnit4::class)
class EndedCoursesBannerTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = CanvasSettings(context)
    private lateinit var db: NoteAppDatabase
    private val now = Instant.now()
    private val ended = Course("c-ended", "ELECTENG 101", "Circuits", 777, now, now)
    private val recent = Course("c-recent", "COMPSYS 201", "Computer Systems", 888, now, now)

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
            db.courseDao().insert(ended)
            db.courseDao().insert(recent)
            settings.setCourseMissingSince(
                mapOf(
                    ended.id to now.minus(Duration.ofDays(20)).toEpochMilli(),
                    recent.id to now.minus(Duration.ofDays(3)).toEpochMilli(), // too soon to ask
                ),
            )
        }
        val viewModel = HomeViewModel(FolderRepository(db), TaskRepository(db), canvasSettings = settings)
        compose.setContent {
            NoteAppTheme { HomeScreen(viewModel, onOpenSettings = {}, onOpenFolder = {}, onOpenTask = {}, onAddTask = {}) }
        }
    }

    @After
    fun tearDown() {
        runBlocking { settings.setCourseMissingSince(emptyMap()) }
        db.close()
    }

    private fun waitForBanner(shown: Boolean) = compose.waitUntil(5_000) {
        compose.onAllNodes(hasTestTag("banner-ended-courses")).fetchSemanticsNodes().isNotEmpty() == shown
    }

    @Test
    fun anEndedCourse_isSuggested_andArchiveArchivesIt() {
        waitForBanner(true)
        compose.onNodeWithText(
            "ELECTENG 101 isn't in your Canvas courses any more. Archive it? Its notes stay; its tasks leave Home.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("archive-ended").performClick()
        compose.waitUntil(5_000) { runBlocking { db.courseDao().getById(ended.id)?.archivedAt != null } }
        assertNull("the recent one isn't touched", runBlocking { db.courseDao().getById(recent.id)?.archivedAt })
        waitForBanner(false)
    }

    @Test
    fun notNow_stopsAsking_withoutArchiving() {
        waitForBanner(true)
        compose.onNodeWithTag("hide-ended").performClick()
        waitForBanner(false)
        assertNull(runBlocking { db.courseDao().getById(ended.id)?.archivedAt })
        assertTrue(ended.id in runBlocking { settings.hiddenArchivePrompts.first() })
        assertNotNull(runBlocking { db.courseDao().getById(ended.id) })
    }
}
