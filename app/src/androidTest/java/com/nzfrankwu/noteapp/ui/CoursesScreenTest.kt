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
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.ui.settings.CoursesScreen
import com.nzfrankwu.noteapp.ui.settings.CoursesViewModel
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Settings → Courses: archive and bring back any course, with or without a folder (B.8). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CoursesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: NoteAppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val folders = FolderRepository(db)
        runBlocking {
            folders.createFolder("Electromagnetics", "ELECTENG 204")
            // A Canvas course without a folder (e.g. a library module).
            db.courseDao().insert(Course("lib", "LIBRARY 101", "Library skills", 900, Instant.EPOCH, Instant.EPOCH))
        }
        compose.setContent {
            NoteAppTheme {
                Box(Modifier.size(width = 1120.dp, height = 800.dp)) {
                    CoursesScreen(CoursesViewModel(folders), onBack = {})
                }
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("LIBRARY 101"), 5_000)
    }

    @Test
    fun listsEveryCourse_withItsFolder() {
        compose.onNodeWithText("Current").assertExists()
        compose.onNodeWithText("Folder: Electromagnetics").assertExists()
        compose.onNodeWithText("No folder").assertExists()
    }

    @Test
    fun archiveAndUnarchive_aCourseWithoutAFolder() {
        compose.onNodeWithTag("archive-LIBRARY 101").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Archived (1)"), 5_000)
        assertNotNull(runBlocking { db.courseDao().getById("lib")!!.archivedAt })

        compose.onNodeWithTag("unarchive-LIBRARY 101").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("archive-LIBRARY 101"), 5_000)
        assertNull(runBlocking { db.courseDao().getById("lib")!!.archivedAt })
        compose.onNodeWithText("Archived (1)").assertDoesNotExist()
    }
}
