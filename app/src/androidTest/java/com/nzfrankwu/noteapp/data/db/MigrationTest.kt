package com.nzfrankwu.noteapp.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.domain.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves app updates keep the user's data (IDEA.md §5a). Uses the exported schemas in
 * app/schemas/ to build databases exactly as older app versions created them.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NoteAppDatabase::class.java)

    @After
    fun tearDown() {
        context.deleteDatabase(testDbName)
    }

    /**
     * From EVERY past version, our migrations must reach exactly the schema the current code
     * expects. Room checks this when the database opens, and throws if anything is off.
     */
    @Test
    fun everyOldVersion_migratesToTheCurrentSchema() {
        for (version in 1..NoteAppDatabase.VERSION) {
            helper.createDatabase(testDbName, version).close()
            openWithAllMigrations().apply {
                openHelper.writableDatabase // opening = migrate + validate
                close()
            }
            context.deleteDatabase(testDbName)
        }
    }

    /**
     * Data saved by version 1 must still be there, unchanged, in the current version.
     * The SQL below is frozen to version 1's tables on purpose: never update it for new versions.
     */
    @Test
    fun dataSavedByVersion1_survivesUpdatingToTheCurrentVersion() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                "INSERT INTO courses (id, code, name, canvas_course_id, created_at, updated_at) " +
                    "VALUES ('c1', 'ELECTENG 204', 'Engineering Electromagnetics', 555, 1000, 2000)",
            )
            execSQL(
                "INSERT INTO folders (id, name, course_id, created_at, updated_at) " +
                    "VALUES ('f1', 'ELECTENG 204', 'c1', 1000, 2000)",
            )
            execSQL(
                "INSERT INTO tasks (id, title, course_id, due_at, url, description_html, status, target_at, " +
                    "notes, source, canvas_assignment_id, canvas_submission_state, canvas_submitted_at, " +
                    "removed_from_canvas, created_at, updated_at) VALUES ('t1', 'Assignment 3', 'c1', " +
                    "1763600000000, 'https://canvas.example.edu/courses/1/assignments/9', '<p>Hi</p>', " +
                    "'stuck', 1763500000000, 'Ask about Q4', 'canvas', 9, 'unsubmitted', NULL, 0, 1000, 2000)",
            )
            close()
        }

        val db = openWithAllMigrations()
        runBlocking {
            val course = requireRow(db.courseDao().getById("c1"))
            assertEquals("ELECTENG 204", course.code)
            assertEquals(555L, course.canvasCourseId)

            val folder = requireRow(db.folderDao().getById("f1"))
            assertEquals("c1", folder.courseId)

            val task = requireRow(db.taskDao().getById("t1"))
            assertEquals("Assignment 3", task.title)
            assertEquals(TaskStatus.STUCK, task.status)
            assertEquals("Ask about Q4", task.notes)
            assertEquals(1763500000000, task.targetAt?.toEpochMilli())
        }
        db.close()
    }

    private fun openWithAllMigrations(): NoteAppDatabase =
        Room.databaseBuilder(context, NoteAppDatabase::class.java, testDbName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    private fun <T : Any> requireRow(row: T?): T {
        assertNotNull("Row is missing after migration", row)
        return row!!
    }
}
