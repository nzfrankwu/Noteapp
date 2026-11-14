package com.nzfrankwu.noteapp.canvas

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.TaskDraft
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A whole Canvas sync against a fake Canvas (no network) and a real in-memory database. */
@RunWith(AndroidJUnit4::class)
class CanvasSyncTest {
    private lateinit var db: NoteAppDatabase
    private var now = Instant.parse("2026-11-14T10:00:00Z")
    private var nextId = 0
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }

    /** A fake Canvas whose data the test controls. */
    private class FakeCanvas : CanvasSource {
        var courses = listOf<CanvasCourse>()
        val assignments = mutableMapOf<Long, List<CanvasAssignment>>()
        val failing = mutableMapOf<Long, CanvasException>()
        override suspend fun getActiveCourses() = courses
        override suspend fun getAssignments(courseId: Long): List<CanvasAssignment> {
            failing[courseId]?.let { throw it }
            return assignments[courseId].orEmpty()
        }
    }

    private val canvas = FakeCanvas()
    private lateinit var sync: CanvasSync

    private val due = Instant.parse("2026-11-20T10:59:00Z")

    private fun assignment(id: Long, courseId: Long = 555, name: String = "A$id", submission: CanvasSubmission? = null) =
        CanvasAssignment(id, courseId, name, due, "<p>$name</p>", "https://canvas.example.edu/courses/$courseId/assignments/$id", submission)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        sync = CanvasSync(db, canvas, clock) { "id-${++nextId}" }
        canvas.courses = listOf(
            CanvasCourse(555, "Engineering Electromagnetics", "ELECTENG 204"),
            CanvasCourse(556, "Circuits and Systems", "ELECTENG 292"),
        )
        canvas.assignments[555] = listOf(assignment(1), assignment(2))
        canvas.assignments[556] = listOf(assignment(10, courseId = 556))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun taskFor(assignmentId: Long) = checkNotNull(db.taskDao().getByCanvasAssignmentId(assignmentId))

    @Test
    fun firstSync_importsCoursesAndAssignments() = runBlocking {
        val summary = sync.sync()
        assertEquals(SyncSummary(courses = 2, tasksAdded = 3, tasksUpdated = 0, tasksMarkedRemoved = 0, failedCourses = emptyList()), summary)
        val courses = db.courseDao().getAll().associateBy { it.canvasCourseId }
        assertEquals("ELECTENG 204", courses.getValue(555).code)
        assertEquals(courses.getValue(555).id, taskFor(1).courseId)
        assertEquals(TaskSource.CANVAS, taskFor(1).source)
    }

    @Test
    fun yourOwnCourse_withTheSameCode_isLinked_notDuplicated() = runBlocking {
        val folders = FolderRepository(db, clock) { "own-${++nextId}" }
        folders.createFolder("EE 204", "electeng204") // you made this course yourself
        val yourCourse = db.courseDao().getAll().single()
        TaskRepository(db, clock) { "own-task-${++nextId}" }.createTask(TaskDraft("Study for test", yourCourse.id))

        sync.sync()

        val linked = checkNotNull(db.courseDao().getById(yourCourse.id))
        assertEquals(555L, linked.canvasCourseId)
        assertEquals(2, db.courseDao().getAll().size) // 204 (linked) + 292 (new), no duplicate
        assertEquals(yourCourse.id, taskFor(1).courseId)
        assertEquals(yourCourse.id, db.folderDao().getAll().single().courseId) // folder still linked
    }

    @Test
    fun secondSync_keepsYourChanges_andOnlyTouchesWhatChanged() = runBlocking {
        sync.sync()
        val task1 = taskFor(1)
        db.taskDao().update(task1.copy(status = TaskStatus.STUCK, notes = "ask tutor"))
        val untouchedBefore = taskFor(2)

        now = now.plusSeconds(3600)
        canvas.assignments[555] = listOf(assignment(1, name = "A1 (extended)"), assignment(2))
        val summary = sync.sync()

        assertEquals(1, summary.tasksUpdated)
        assertEquals(0, summary.tasksAdded)
        val after = taskFor(1)
        assertEquals("A1 (extended)", after.title)
        assertEquals(TaskStatus.STUCK, after.status)
        assertEquals("ask tutor", after.notes)
        assertEquals("Unchanged task keeps its 'last changed' time", untouchedBefore, taskFor(2))
    }

    @Test
    fun submittedOnCanvas_movesTaskToSubmitted() = runBlocking {
        sync.sync()
        canvas.assignments[555] = listOf(assignment(1, submission = CanvasSubmission("submitted", now)), assignment(2))
        sync.sync()
        assertEquals(TaskStatus.SUBMITTED, taskFor(1).status)
        assertEquals(TaskStatus.NOT_STARTED, taskFor(2).status)
    }

    @Test
    fun assignmentGoneFromCanvas_isMarkedRemoved_notDeleted() = runBlocking {
        sync.sync()
        canvas.assignments[555] = listOf(assignment(1))
        val summary = sync.sync()
        assertEquals(1, summary.tasksMarkedRemoved)
        assertTrue(taskFor(2).removedFromCanvas)
        assertFalse(taskFor(1).removedFromCanvas)
    }

    @Test
    fun aCourseThatFailsToLoad_isSkipped_andNothingInItIsMarkedRemoved() = runBlocking {
        sync.sync()
        canvas.failing[555] = CanvasException.HttpError(500)
        canvas.assignments[556] = listOf(assignment(10, courseId = 556), assignment(11, courseId = 556))

        val summary = sync.sync()

        assertEquals(listOf("ELECTENG 204"), summary.failedCourses)
        assertFalse(taskFor(1).removedFromCanvas)
        assertFalse(taskFor(2).removedFromCanvas)
        assertNotNull("The other course still synced", db.taskDao().getByCanvasAssignmentId(11))
    }

    @Test
    fun signInExpired_stopsTheSync() = runBlocking {
        canvas.failing[555] = CanvasException.SessionExpired()
        assertThrows(CanvasException.SessionExpired::class.java) { runBlocking { sync.sync() } }
        Unit
    }

    @Test
    fun courseListFailing_changesNothing() = runBlocking {
        val broken = object : CanvasSource {
            override suspend fun getActiveCourses(): List<CanvasCourse> = throw CanvasException.HttpError(503)
            override suspend fun getAssignments(courseId: Long): List<CanvasAssignment> = error("not reached")
        }
        assertThrows(CanvasException.HttpError::class.java) { runBlocking { CanvasSync(db, broken, clock).sync() } }
        assertTrue(db.courseDao().getAll().isEmpty())
    }

    @Test
    fun courseNoLongerActive_keepsItsTasksUntouched() = runBlocking {
        sync.sync()
        canvas.courses = listOf(CanvasCourse(556, "Circuits and Systems", "ELECTENG 292")) // 204 finished
        sync.sync()
        assertFalse(taskFor(1).removedFromCanvas)
        assertFalse(taskFor(2).removedFromCanvas)
    }
}
