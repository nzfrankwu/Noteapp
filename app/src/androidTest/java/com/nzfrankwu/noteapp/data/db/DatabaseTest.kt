package com.nzfrankwu.noteapp.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Checks the real database (SQLite on the emulator) stores data and enforces its rules. */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: NoteAppDatabase

    private val t0 = Instant.parse("2026-11-14T10:00:00Z")

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun course(id: String = "course-1", code: String = "ELECTENG 204", canvasId: Long? = null) =
        Course(id = id, code = code, name = "Engineering Electromagnetics", canvasCourseId = canvasId, createdAt = t0, updatedAt = t0)

    private fun folder(id: String = "folder-1", name: String = "ELECTENG 204", courseId: String? = null) =
        Folder(id = id, name = name, courseId = courseId, createdAt = t0, updatedAt = t0)

    private fun task(id: String = "task-1", courseId: String? = null, canvasAssignmentId: Long? = null) =
        Task(
            id = id, title = "Study for Test 2", courseId = courseId, dueAt = null, url = null,
            descriptionHtml = null, status = TaskStatus.NOT_STARTED, targetAt = null, notes = null,
            source = TaskSource.MANUAL, canvasAssignmentId = canvasAssignmentId, canvasSubmissionState = null,
            canvasSubmittedAt = null, removedFromCanvas = false, createdAt = t0, updatedAt = t0,
        )

    @Test
    fun task_withEveryFieldFilledIn_isStoredExactly() = runBlocking {
        db.courseDao().insert(course(canvasId = 555))
        val full = Task(
            id = "task-1", title = "Assignment 3", courseId = "course-1",
            dueAt = Instant.parse("2026-11-20T10:59:00Z"), url = "https://canvas.example.edu/courses/1/assignments/9",
            descriptionHtml = "<p>Answer <b>all</b> questions.</p>", status = TaskStatus.DOUBLE_CHECKING,
            targetAt = Instant.parse("2026-11-18T08:00:00Z"), notes = "Ask about Q4",
            source = TaskSource.CANVAS, canvasAssignmentId = 9, canvasSubmissionState = "submitted",
            canvasSubmittedAt = Instant.parse("2026-11-19T09:30:00Z"), removedFromCanvas = true,
            createdAt = t0, updatedAt = t0.plusSeconds(60), availableAt = Instant.parse("2026-11-09T19:00:00Z"),
        )
        db.taskDao().insert(full)
        assertEquals(full, db.taskDao().getById("task-1"))
    }

    @Test
    fun task_withOptionalFieldsEmpty_isStoredExactly() = runBlocking {
        val minimal = task()
        db.taskDao().insert(minimal)
        assertEquals(minimal, db.taskDao().getById("task-1"))
    }

    @Test
    fun folders_areListedAlphabetically_ignoringCase() = runBlocking {
        db.folderDao().insert(folder(id = "f1", name = "b folder"))
        db.folderDao().insert(folder(id = "f2", name = "A folder"))
        db.folderDao().insert(folder(id = "f3", name = "c folder"))
        assertEquals(listOf("A folder", "b folder", "c folder"), db.folderDao().observeAll().first().map { it.name })
    }

    @Test
    fun liveList_updatesAfterAChange() = runBlocking {
        assertEquals(0, db.taskDao().observeAll().first().size)
        db.taskDao().insert(task())
        assertEquals(1, db.taskDao().observeAll().first().size)
    }

    @Test
    fun deletingAFolder_keepsTheCourseAndItsTasks() = runBlocking {
        db.courseDao().insert(course())
        db.folderDao().insert(folder(courseId = "course-1"))
        db.taskDao().insert(task(courseId = "course-1"))

        db.folderDao().delete(folder(courseId = "course-1"))

        assertNotNull(db.courseDao().getById("course-1"))
        assertEquals("course-1", db.taskDao().getById("task-1")?.courseId)
    }

    @Test
    fun deletingACourse_keepsItsTasksAndFolder_justUnlinked() = runBlocking {
        db.courseDao().insert(course())
        db.folderDao().insert(folder(courseId = "course-1"))
        db.taskDao().insert(task(courseId = "course-1"))

        db.courseDao().delete(course())

        val task = db.taskDao().getById("task-1")
        assertNotNull("Task must survive its course being deleted", task)
        assertNull(task?.courseId)
        val folder = db.folderDao().getById("folder-1")
        assertNotNull("Folder must survive its course being deleted", folder)
        assertNull(folder?.courseId)
    }

    @Test
    fun updatingACourse_keepsItsFolderAndTasksLinked() = runBlocking {
        db.courseDao().insert(course())
        db.folderDao().insert(folder(courseId = "course-1"))
        db.taskDao().insert(task(courseId = "course-1"))

        db.courseDao().upsert(course().copy(name = "New name", canvasCourseId = 777))

        assertEquals("New name", db.courseDao().getById("course-1")?.name)
        assertEquals("course-1", db.taskDao().getById("task-1")?.courseId)
        assertEquals("course-1", db.folderDao().getById("folder-1")?.courseId)
    }

    @Test
    fun aCanvasAssignment_canOnlyBeStoredOnce() = runBlocking {
        db.taskDao().insert(task(id = "task-1", canvasAssignmentId = 42))
        val second = runCatching { db.taskDao().insert(task(id = "task-2", canvasAssignmentId = 42)) }
        assertTrue(second.exceptionOrNull() is SQLiteConstraintException)
    }

    @Test
    fun manyOwnTasks_canHaveNoCanvasId() = runBlocking {
        db.taskDao().insert(task(id = "task-1"))
        db.taskDao().insert(task(id = "task-2"))
        assertEquals(2, db.taskDao().observeAll().first().size)
    }

    @Test
    fun aCourse_canOnlyBeLinkedToOneFolder() = runBlocking {
        db.courseDao().insert(course())
        db.folderDao().insert(folder(id = "f1", courseId = "course-1"))
        val second = runCatching { db.folderDao().insert(folder(id = "f2", courseId = "course-1")) }
        assertTrue(second.exceptionOrNull() is SQLiteConstraintException)
    }

    @Test
    fun aCanvasCourse_canOnlyBeStoredOnce() = runBlocking {
        db.courseDao().insert(course(id = "c1", canvasId = 555))
        val second = runCatching { db.courseDao().insert(course(id = "c2", canvasId = 555)) }
        assertTrue(second.exceptionOrNull() is SQLiteConstraintException)
        assertEquals("c1", db.courseDao().getByCanvasId(555)?.id)
    }

    // ---- Archived courses (database version 3) ----

    @Test
    fun archivedCourse_isStoredExactly() = runBlocking {
        val archived = course().copy(archivedAt = t0.plusSeconds(3600))
        db.courseDao().insert(archived)
        assertEquals(archived, db.courseDao().getById("course-1"))
    }

    @Test
    fun homeLeavesOutArchivedCoursesTasks_butKeepsTasksWithoutACourse() = runBlocking {
        db.courseDao().insert(course(id = "c1"))
        db.courseDao().insert(course(id = "old", code = "ENGGEN 115").copy(archivedAt = t0))
        db.taskDao().insert(task(id = "current", courseId = "c1"))
        db.taskDao().insert(task(id = "archived", courseId = "old"))
        db.taskDao().insert(task(id = "no course", courseId = null))

        assertEquals(setOf("current", "no course"), db.taskDao().observeActiveWithCourse().first().map { it.task.id }.toSet())
        val all = db.taskDao().observeAllWithCourse().first().associateBy { it.task.id }
        assertEquals(3, all.size)
        assertTrue(all.getValue("archived").isCourseArchived)
        assertTrue(!all.getValue("current").isCourseArchived)
        // The folder still has all of its own tasks.
        assertEquals(listOf("archived"), db.taskDao().observeForCourse("old").first().map { it.id })
    }

    @Test
    fun foldersKnowWhenTheirCourseIsArchived() = runBlocking {
        db.courseDao().insert(course(id = "c1"))
        db.courseDao().insert(course(id = "old", code = "ENGGEN 115").copy(archivedAt = t0))
        db.folderDao().insert(folder(id = "f1", name = "Current", courseId = "c1"))
        db.folderDao().insert(folder(id = "f2", name = "Old", courseId = "old"))
        db.folderDao().insert(folder(id = "f3", name = "Personal", courseId = null))
        val folders = db.folderDao().observeAllWithCourse().first().associateBy { it.folder.id }
        assertTrue(!folders.getValue("f1").isArchived)
        assertTrue(folders.getValue("f2").isArchived)
        assertTrue(!folders.getValue("f3").isArchived)
        assertTrue(db.folderDao().observeWithCourse("f2").first()!!.isArchived)
    }

    @Test
    fun archivedCanvasCourses_areNeverSuggestedForAFolder() = runBlocking {
        db.courseDao().insert(course(id = "c1", code = "ELECTENG 204", canvasId = 1))
        db.courseDao().insert(course(id = "c2", code = "ENGGEN 115", canvasId = 2).copy(archivedAt = t0))
        assertEquals(listOf("c1"), db.courseDao().observeCanvasCoursesWithoutFolder().first().map { it.id })
    }

    @Test
    fun folderCourseChoices_areCurrentCoursesWithoutAFolder() = runBlocking {
        db.courseDao().insert(course(id = "free", code = "ELECTENG 292"))
        db.courseDao().insert(course(id = "linked", code = "ELECTENG 204"))
        db.courseDao().insert(course(id = "old", code = "ENGGEN 115").copy(archivedAt = t0))
        db.folderDao().insert(folder(id = "f1", courseId = "linked"))
        assertEquals(listOf("free"), db.courseDao().observeCurrentWithoutFolder().first().map { it.id })
    }

    @Test
    fun courseList_hasEveryCourse_withItsFolder() = runBlocking {
        db.courseDao().insert(course(id = "c1", code = "ELECTENG 204"))
        db.courseDao().insert(course(id = "c2", code = "COMPSYS 201").copy(archivedAt = t0))
        db.folderDao().insert(folder(id = "f1", name = "Electromagnetics", courseId = "c1"))
        val list = db.courseDao().observeAllWithFolder().first()
        assertEquals(listOf("COMPSYS 201", "ELECTENG 204"), list.map { it.course.code })
        assertEquals(listOf(null, "Electromagnetics"), list.map { it.folderName })
        assertEquals(listOf(true, false), list.map { it.isArchived })
    }

    @Test
    fun courseTasks_onlyIncludeThatCourse() = runBlocking {
        db.courseDao().insert(course(id = "c1"))
        db.courseDao().insert(course(id = "c2", code = "ELECTENG 292"))
        db.taskDao().insert(task(id = "t1", courseId = "c1"))
        db.taskDao().insert(task(id = "t2", courseId = "c2"))
        db.taskDao().insert(task(id = "t3", courseId = null))
        assertEquals(listOf("t1"), db.taskDao().observeForCourse("c1").first().map { it.id })
    }
}
