package com.nzfrankwu.noteapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {
    private lateinit var db: NoteAppDatabase
    private lateinit var repo: TaskRepository
    private var now = Instant.parse("2026-11-14T10:00:00Z")
    private var nextId = 0
    private val due = Instant.parse("2026-11-20T10:59:00Z")
    private val target = Instant.parse("2026-11-18T20:00:00Z")

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val clock = object : Clock() {
            override fun instant() = now
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?) = this
        }
        repo = TaskRepository(db, clock) { "task-${++nextId}" }
        db.courseDao().insert(Course("c1", "ELECTENG 204", null, null, now, now))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun task(id: String) = checkNotNull(db.taskDao().getById(id))

    private suspend fun insertCanvasTask(id: String = "canvas-1", removed: Boolean = false) {
        db.taskDao().insert(
            Task(
                id = id, title = "Assignment 3", courseId = "c1", dueAt = due, url = "https://canvas.example.edu/a/3",
                descriptionHtml = "<p>Do it</p>", status = TaskStatus.NOT_STARTED, targetAt = null, notes = null,
                source = TaskSource.CANVAS, canvasAssignmentId = 3, canvasSubmissionState = "unsubmitted",
                canvasSubmittedAt = null, removedFromCanvas = removed, createdAt = now, updatedAt = now,
            ),
        )
    }

    @Test
    fun createTask_savesATidyManualTask() = runBlocking {
        val result = repo.createTask(
            TaskDraft("  Study for Test 2 ", "c1", dueAt = due, targetAt = target, url = "example.com/notes", notes = "  ch 3-5 "),
        )
        assertEquals(TaskResult.Success("task-1"), result)
        val saved = task("task-1")
        assertEquals("Study for Test 2", saved.title)
        assertEquals(TaskStatus.NOT_STARTED, saved.status)
        assertEquals(TaskSource.MANUAL, saved.source)
        assertEquals(due, saved.dueAt)
        assertEquals(target, saved.targetAt)
        assertEquals("https://example.com/notes", saved.url)
        assertEquals("ch 3-5", saved.notes)
        assertEquals(now, saved.createdAt)
    }

    @Test
    fun createTask_withoutDates_isAllowed() = runBlocking {
        assertTrue(repo.createTask(TaskDraft("Read chapter 3", "c1")) is TaskResult.Success)
        assertNull(task("task-1").dueAt)
    }

    @Test
    fun createTask_blankTitle_isRefused() = runBlocking {
        assertEquals(TaskResult.Failure(TaskError.BlankTitle), repo.createTask(TaskDraft("  ", "c1")))
        assertTrue(repo.observeAllWithCourse().first().isEmpty())
    }

    @Test
    fun createTask_needsAnExistingCourse() = runBlocking {
        assertEquals(TaskResult.Failure(TaskError.NoCourse), repo.createTask(TaskDraft("Lab", null)))
        assertEquals(TaskResult.Failure(TaskError.NoCourse), repo.createTask(TaskDraft("Lab", "no-such-course")))
    }

    @Test
    fun updateTask_ownTask_changesEverything() = runBlocking {
        repo.createTask(TaskDraft("Old", "c1", dueAt = due))
        now = now.plusSeconds(60)
        repo.updateTask("task-1", TaskDraft("New", "c1", dueAt = null, targetAt = target, url = "", notes = "n"))
        val saved = task("task-1")
        assertEquals("New", saved.title)
        assertNull(saved.dueAt)
        assertEquals(target, saved.targetAt)
        assertNull(saved.url)
        assertEquals("n", saved.notes)
        assertEquals(now, saved.updatedAt)
    }

    @Test
    fun updateTask_canvasTask_onlyChangesYourFields() = runBlocking {
        insertCanvasTask()
        repo.updateTask("canvas-1", TaskDraft("Hacked title", "c1", dueAt = null, targetAt = target, url = "x", notes = "mine"))
        val saved = task("canvas-1")
        assertEquals("Assignment 3", saved.title)
        assertEquals(due, saved.dueAt)
        assertEquals("https://canvas.example.edu/a/3", saved.url)
        assertEquals(target, saved.targetAt)
        assertEquals("mine", saved.notes)
    }

    @Test
    fun setStatus_changesStatusAndTime() = runBlocking {
        repo.createTask(TaskDraft("Lab", "c1"))
        now = now.plusSeconds(60)
        repo.setStatus("task-1", TaskStatus.STUCK)
        assertEquals(TaskStatus.STUCK, task("task-1").status)
        assertEquals(now, task("task-1").updatedAt)
    }

    @Test
    fun deleteOwnTask_thenUndo_bringsBackTheExactSameTask() = runBlocking {
        repo.createTask(TaskDraft("Lab", "c1", dueAt = due, notes = "keep me"))
        val before = task("task-1")
        val result = repo.deleteTask("task-1") as DeleteTaskResult.Deleted
        assertNull(db.taskDao().getById("task-1"))
        repo.restoreTask(result.task)
        assertEquals(before, task("task-1"))
    }

    @Test
    fun canvasTask_cantBeDeleted() = runBlocking {
        insertCanvasTask()
        assertEquals(DeleteTaskResult.Failure(TaskError.CanvasTaskCantBeDeleted), repo.deleteTask("canvas-1"))
        task("canvas-1") // still there
        Unit
    }

    @Test
    fun canvasTaskRemovedFromCanvas_canBeDeleted() = runBlocking {
        insertCanvasTask(removed = true)
        assertTrue(repo.deleteTask("canvas-1") is DeleteTaskResult.Deleted)
        assertNull(db.taskDao().getById("canvas-1"))
    }

    @Test
    fun undo_afterTheCourseWasDeleted_bringsTheTaskBackWithoutACourse() = runBlocking {
        repo.createTask(TaskDraft("Lab", "c1"))
        val deleted = (repo.deleteTask("task-1") as DeleteTaskResult.Deleted).task
        db.courseDao().delete(checkNotNull(db.courseDao().getById("c1")))
        repo.restoreTask(deleted)
        assertNull(task("task-1").courseId)
    }

    @Test
    fun taskList_includesCourseCodes() = runBlocking {
        repo.createTask(TaskDraft("Lab", "c1"))
        assertEquals("ELECTENG 204", repo.observeAllWithCourse().first().single().courseCode)
        assertEquals("ELECTENG 204", repo.observeTask("task-1").first()?.courseCode)
    }
}
