package com.nzfrankwu.noteapp.data.db

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.canvas.CanvasAssignment
import com.nzfrankwu.noteapp.canvas.CanvasCourse
import com.nzfrankwu.noteapp.canvas.CanvasSource
import com.nzfrankwu.noteapp.canvas.CanvasSubmission
import com.nzfrankwu.noteapp.canvas.CanvasSync
import com.nzfrankwu.noteapp.canvas.SyncSummary
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Proves app updates keep the user's data (IDEA.md §5a). Uses the exported schemas in
 * app/schemas/ to build databases exactly as older app versions created them.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDbName = "migration-test.db"
    private val freshDbName = "migration-test-fresh.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), NoteAppDatabase::class.java)

    @After
    fun tearDown() {
        context.deleteDatabase(testDbName)
        context.deleteDatabase(freshDbName)
    }

    // ---- Every version ----

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
     * A fresh install and an upgraded install must have the same columns in the same order
     * (Migrations.kt: new columns go last). Otherwise a later "copy the rows" migration could put
     * values into the wrong columns on one of them.
     */
    @Test
    fun freshAndUpgradedDatabases_haveTheSameColumns_inTheSameOrder() {
        helper.createDatabase(testDbName, 1).close()
        val upgraded = helper.runMigrationsAndValidate(testDbName, NoteAppDatabase.VERSION, true, *ALL_MIGRATIONS)
        val fresh = helper.createDatabase(freshDbName, NoteAppDatabase.VERSION)
        for (table in listOf("courses", "folders", "tasks", "notebooks", "pages")) {
            assertEquals("Columns of $table", columnsOf(fresh, table), columnsOf(upgraded, table))
        }
        upgraded.close()
        fresh.close()
    }

    // ---- Version 1 → 2: tasks.available_at ----

    /** Every row and every value saved by version 1 is still there after 1 → 2, untouched. */
    @Test
    fun migration1To2_keepsEveryValue_andAddsAnEmptyAvailableAt() {
        val before = helper.createDatabase(testDbName, 1).let { old ->
            insertVersion1Data(old)
            version1Columns.mapValues { (table, columns) -> rowsOf(old, table, columns) }.also { old.close() }
        }

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        for ((table, columns) in version1Columns) {
            assertEquals("Rows of $table after migrating", before.getValue(table), rowsOf(db, table, columns))
        }
        assertEquals(
            "Every task's 'available from' starts empty",
            listOf(listOf(null), listOf(null)),
            rowsOf(db, "tasks", listOf("available_at")),
        )
        db.query("PRAGMA foreign_key_check").use { assertFalse("Broken links between tables", it.moveToFirst()) }
        db.close()
    }

    /**
     * Data saved by version 1 must still be there, unchanged, when the current app opens it.
     * The rows come from [insertVersion1Data], which is frozen to version 1's tables.
     */
    @Test
    fun dataSavedByVersion1_survivesUpdatingToTheCurrentVersion() {
        helper.createDatabase(testDbName, 1).apply {
            insertVersion1Data(this)
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
            assertEquals("c1", task.courseId)
            assertEquals(1763600000000, task.dueAt?.toEpochMilli())
            assertEquals("https://canvas.example.edu/courses/1/assignments/9", task.url)
            assertEquals("<p>Hi</p>", task.descriptionHtml)
            assertEquals(TaskStatus.STUCK, task.status)
            assertEquals(1763500000000, task.targetAt?.toEpochMilli())
            assertEquals("Ask about Q4", task.notes)
            assertEquals(TaskSource.CANVAS, task.source)
            assertEquals(9L, task.canvasAssignmentId)
            assertEquals("unsubmitted", task.canvasSubmissionState)
            assertNull(task.canvasSubmittedAt)
            assertFalse(task.removedFromCanvas)
            assertEquals(1000L, task.createdAt.toEpochMilli())
            assertEquals(2000L, task.updatedAt.toEpochMilli())
            assertNull(task.availableAt)
            assertNull("Courses start out current", course.archivedAt)

            val own = requireRow(db.taskDao().getById("t2"))
            assertEquals("Study for Test 2", own.title)
            assertEquals(TaskSource.MANUAL, own.source)
            assertNull(own.availableAt)
        }
        db.close()
    }

    /**
     * What happens on the tablet: the update migrates the database, then the next Canvas sync
     * fills in "available from" for Canvas tasks. Your status, target date and notes stay, no task
     * is duplicated, and your own tasks are untouched.
     */
    @Test
    fun afterUpdating_theNextCanvasSync_fillsInAvailableFrom_andKeepsYourFields() {
        helper.createDatabase(testDbName, 1).apply {
            insertVersion1Data(this)
            close()
        }
        val opens = Instant.parse("2025-11-09T19:00:00Z")
        val canvas = object : CanvasSource {
            override suspend fun getActiveCourses() = listOf(CanvasCourse(555, "Engineering Electromagnetics", "ELECTENG 204"))
            override suspend fun getAssignments(courseId: Long) = listOf(
                // The same assignment as task t1, now with Canvas's "available from".
                CanvasAssignment(
                    id = 9, courseId = 555, name = "Assignment 3", dueAt = Instant.ofEpochMilli(1763600000000),
                    descriptionHtml = "<p>Hi</p>", htmlUrl = "https://canvas.example.edu/courses/1/assignments/9",
                    submission = CanvasSubmission("unsubmitted", null), unlockAt = opens,
                ),
            )
        }
        val now = Instant.parse("2025-11-15T09:00:00Z")
        val clock = object : Clock() {
            override fun instant() = now
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?) = this
        }

        val db = openWithAllMigrations()
        runBlocking {
            val summary = CanvasSync(db, canvas, clock) { error("no new IDs expected") }.sync()
            assertEquals(SyncSummary(courses = 1, tasksAdded = 0, tasksUpdated = 1, tasksMarkedRemoved = 0, failedCourses = emptyList()), summary)

            val task = requireRow(db.taskDao().getById("t1"))
            assertEquals(opens, task.availableAt)
            assertEquals(TaskStatus.STUCK, task.status)
            assertEquals(1763500000000, task.targetAt?.toEpochMilli())
            assertEquals("Ask about Q4", task.notes)
            assertEquals(1000L, task.createdAt.toEpochMilli())
            assertEquals(now, task.updatedAt)

            val own = requireRow(db.taskDao().getById("t2"))
            assertNull(own.availableAt)
            assertEquals(2000L, own.updatedAt.toEpochMilli())
            assertEquals("No task was duplicated", 2, db.taskDao().observeAll().first().size)
            assertEquals("c1", requireRow(db.folderDao().getById("f1")).courseId)
        }
        db.close()
    }

    // ---- Version 2 → 3: courses.archived_at ----

    /** Every row and every value saved by version 2 is still there after 2 → 3, untouched. */
    @Test
    fun migration2To3_keepsEveryValue_andEveryCourseStartsCurrent() {
        val before = helper.createDatabase(testDbName, 2).let { old ->
            insertVersion2Data(old)
            version2Columns.mapValues { (table, columns) -> rowsOf(old, table, columns) }.also { old.close() }
        }

        val db = helper.runMigrationsAndValidate(testDbName, 3, true, MIGRATION_2_3)

        for ((table, columns) in version2Columns) {
            assertEquals("Rows of $table after migrating", before.getValue(table), rowsOf(db, table, columns))
        }
        assertEquals(
            "No course is archived after the update",
            listOf(listOf(null), listOf(null), listOf(null)),
            rowsOf(db, "courses", listOf("archived_at")),
        )
        db.query("PRAGMA foreign_key_check").use { assertFalse("Broken links between tables", it.moveToFirst()) }
        db.close()
    }

    /** Data saved by version 2 (incl. "available from") is still there when the current app opens it. */
    @Test
    fun dataSavedByVersion2_survivesUpdatingToTheCurrentVersion() {
        helper.createDatabase(testDbName, 2).apply {
            insertVersion2Data(this)
            close()
        }

        val db = openWithAllMigrations()
        runBlocking {
            val course = requireRow(db.courseDao().getById("c3"))
            assertEquals("COMPSYS 201", course.code)
            assertNull(course.archivedAt)

            val task = requireRow(db.taskDao().getById("t3"))
            assertEquals("Assignment 1", task.title)
            assertEquals(1762900000000, task.availableAt?.toEpochMilli())
            assertEquals(TaskStatus.STARTED, task.status)
            assertEquals("Q2 is hard", task.notes)

            // Every task still shows on Home after the update: nothing is archived yet.
            assertEquals(3, db.taskDao().observeActiveWithCourse().first().size)
            assertEquals("f2", requireRow(db.folderDao().getById("f2")).id)
        }
        db.close()
    }

    // ---- Version 3 → 4: subfolders, notebooks, pages ----

    /**
     * Every row and every value saved by version 3 is still there after 3 → 4, untouched; every
     * folder stays top-level, and the new tables start empty.
     */
    @Test
    fun migration3To4_keepsEveryValue_andEveryFolderStaysTopLevel() {
        val before = helper.createDatabase(testDbName, 3).let { old ->
            insertVersion3Data(old)
            version3Columns.mapValues { (table, columns) -> rowsOf(old, table, columns) }.also { old.close() }
        }

        val db = helper.runMigrationsAndValidate(testDbName, 4, true, MIGRATION_3_4)

        for ((table, columns) in version3Columns) {
            assertEquals("Rows of $table after migrating", before.getValue(table), rowsOf(db, table, columns))
        }
        assertEquals(
            "Every folder is still top-level",
            listOf(listOf(null), listOf(null)),
            rowsOf(db, "folders", listOf("parent_id")),
        )
        db.query("SELECT COUNT(*) FROM notebooks").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM pages").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("PRAGMA foreign_key_check").use { assertFalse("Broken links between tables", it.moveToFirst()) }
        db.close()
    }

    /**
     * After updating, the current app sees version 3's data as before (folders on Home, an
     * archived course still archived) and can make subfolders and notebooks in an old folder.
     */
    @Test
    fun dataSavedByVersion3_survivesUpdatingToTheCurrentVersion_andTakesSubfoldersAndNotebooks() {
        helper.createDatabase(testDbName, 3).apply {
            insertVersion3Data(this)
            close()
        }

        val db = openWithAllMigrations()
        runBlocking {
            val archived = requireRow(db.courseDao().getById("c2"))
            assertEquals(4000L, archived.archivedAt?.toEpochMilli())

            val home = db.folderDao().observeTopLevelWithCourse().first()
            assertEquals(listOf("f2", "f1"), home.map { it.folder.id }) // "Computer Systems", "ELECTENG 204"
            assertEquals("ELECTENG 204", home.single { it.folder.id == "f1" }.courseCode)

            val folders = com.nzfrankwu.noteapp.data.FolderRepository(db, newId = idsFrom("s"))
            val sub = folders.createSubfolder("f1", "Tutorials") as com.nzfrankwu.noteapp.data.FolderResult.Success
            val notebooks = com.nzfrankwu.noteapp.data.NotebookRepository(db, newId = idsFrom("n"))
            val nb = notebooks.createNotebook(sub.folderId, "Tutorial 1") as com.nzfrankwu.noteapp.data.NotebookResult.Success

            assertEquals(listOf("f2", "f1"), db.folderDao().observeTopLevelWithCourse().first().map { it.folder.id })
            assertEquals(1, db.folderDao().observeChildren("f1").first().single().itemCount)
            assertEquals(1, notebooks.getPages(nb.id).size)
            val place = requireRow(folders.observePlace(sub.folderId).first())
            assertEquals("c1", place.course?.id)

            val task = requireRow(db.taskDao().getById("t1"))
            assertEquals(TaskStatus.STUCK, task.status)
            assertEquals("Ask about Q4", task.notes)
        }
        db.close()
    }

    // ---- Helpers ----

    private fun idsFrom(prefix: String): () -> String {
        var n = 0
        return { "$prefix${n++}" }
    }

    /**
     * Rows as version 3 saved them: version 2's rows, with ENGGEN 115 archived.
     * FROZEN to version 3's tables on purpose: never update this SQL for new versions.
     */
    private fun insertVersion3Data(db: SupportSQLiteDatabase) {
        insertVersion2Data(db) // version 2's tables are part of version 3 (only a column was added)
        db.execSQL("UPDATE courses SET archived_at = 4000 WHERE id = 'c2'")
    }

    /** Version 3's columns: version 2's plus courses.archived_at. FROZEN like [insertVersion3Data]. */
    private val version3Columns: Map<String, List<String>> by lazy {
        version2Columns.mapValues { (table, columns) -> if (table == "courses") columns + "archived_at" else columns }
    }

    /**
     * Rows as version 1 saved them: a Canvas course with a folder, a Canvas task with your own
     * status, target date and notes, and one of your own tasks.
     * FROZEN to version 1's tables on purpose: never update this SQL for new versions.
     */
    private fun insertVersion1Data(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO courses (id, code, name, canvas_course_id, created_at, updated_at) " +
                "VALUES ('c1', 'ELECTENG 204', 'Engineering Electromagnetics', 555, 1000, 2000)",
        )
        db.execSQL(
            "INSERT INTO courses (id, code, name, canvas_course_id, created_at, updated_at) " +
                "VALUES ('c2', 'ENGGEN 115', NULL, NULL, 1500, 1500)",
        )
        db.execSQL(
            "INSERT INTO folders (id, name, course_id, created_at, updated_at) " +
                "VALUES ('f1', 'ELECTENG 204', 'c1', 1000, 2000)",
        )
        db.execSQL(
            "INSERT INTO tasks (id, title, course_id, due_at, url, description_html, status, target_at, " +
                "notes, source, canvas_assignment_id, canvas_submission_state, canvas_submitted_at, " +
                "removed_from_canvas, created_at, updated_at) VALUES ('t1', 'Assignment 3', 'c1', " +
                "1763600000000, 'https://canvas.example.edu/courses/1/assignments/9', '<p>Hi</p>', " +
                "'stuck', 1763500000000, 'Ask about Q4', 'canvas', 9, 'unsubmitted', NULL, 0, 1000, 2000)",
        )
        db.execSQL(
            "INSERT INTO tasks (id, title, course_id, due_at, url, description_html, status, target_at, " +
                "notes, source, canvas_assignment_id, canvas_submission_state, canvas_submitted_at, " +
                "removed_from_canvas, created_at, updated_at) VALUES ('t2', 'Study for Test 2', 'c2', " +
                "NULL, NULL, NULL, 'not_started', NULL, NULL, 'manual', NULL, NULL, NULL, 0, 1500, 2000)",
        )
    }

    /**
     * Rows as version 2 saved them: version 1's rows plus a course and folder for COMPSYS 201 and a
     * Canvas task with "available from" (tasks.available_at, new in version 2).
     * FROZEN to version 2's tables on purpose: never update this SQL for new versions.
     */
    private fun insertVersion2Data(db: SupportSQLiteDatabase) {
        insertVersion1Data(db) // version 1's tables are part of version 2 (only a column was added)
        db.execSQL(
            "INSERT INTO courses (id, code, name, canvas_course_id, created_at, updated_at) " +
                "VALUES ('c3', 'COMPSYS 201', 'Computer Systems', 557, 3000, 3000)",
        )
        db.execSQL(
            "INSERT INTO folders (id, name, course_id, created_at, updated_at) " +
                "VALUES ('f2', 'Computer Systems', 'c3', 3000, 3000)",
        )
        db.execSQL(
            "INSERT INTO tasks (id, title, course_id, due_at, url, description_html, status, target_at, " +
                "notes, source, canvas_assignment_id, canvas_submission_state, canvas_submitted_at, " +
                "removed_from_canvas, created_at, updated_at, available_at) VALUES ('t3', 'Assignment 1', 'c3', " +
                "1763700000000, 'https://canvas.example.edu/courses/557/assignments/21', NULL, 'started', NULL, " +
                "'Q2 is hard', 'canvas', 21, 'unsubmitted', NULL, 0, 3000, 3500, 1762900000000)",
        )
    }

    /** Version 2's columns: version 1's plus tasks.available_at. FROZEN like [insertVersion2Data]. */
    private val version2Columns: Map<String, List<String>> by lazy {
        version1Columns.mapValues { (table, columns) -> if (table == "tasks") columns + "available_at" else columns }
    }

    /** Version 1's columns, table by table. FROZEN like [insertVersion1Data]. */
    private val version1Columns = mapOf(
        "courses" to listOf("id", "code", "name", "canvas_course_id", "created_at", "updated_at"),
        "folders" to listOf("id", "name", "course_id", "created_at", "updated_at"),
        "tasks" to listOf(
            "id", "title", "course_id", "due_at", "url", "description_html", "status", "target_at", "notes",
            "source", "canvas_assignment_id", "canvas_submission_state", "canvas_submitted_at",
            "removed_from_canvas", "created_at", "updated_at",
        ),
    )

    /** Every row of [table] (by ID), with the raw stored value of each of [columns]. */
    private fun rowsOf(db: SupportSQLiteDatabase, table: String, columns: List<String>): List<List<Any?>> =
        db.query("SELECT ${columns.joinToString { "`$it`" }} FROM `$table` ORDER BY `id`").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(columns.indices.map { cursor.rawValue(it) })
            }
        }

    private fun Cursor.rawValue(index: Int): Any? = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
        Cursor.FIELD_TYPE_BLOB -> getBlob(index).toList()
        else -> getString(index)
    }

    /** Column name, type, not-null and default of every column of [table], in table order. */
    private fun columnsOf(db: SupportSQLiteDatabase, table: String): List<List<Any?>> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        listOf(
                            cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            cursor.getString(cursor.getColumnIndexOrThrow("type")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("notnull")),
                            cursor.rawValue(cursor.getColumnIndexOrThrow("dflt_value")),
                        ),
                    )
                }
            }
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
