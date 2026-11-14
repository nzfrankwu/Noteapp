package com.nzfrankwu.noteapp.canvas

import androidx.room.withTransaction
import com.nzfrankwu.noteapp.data.db.Course
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.domain.CourseCodes
import com.nzfrankwu.noteapp.domain.TaskSource
import java.time.Clock
import java.util.UUID

/** Where sync reads Canvas data from: the real [CanvasClient], or a fake in tests. */
interface CanvasSource {
    /** All active courses (complete list, or throws). */
    suspend fun getActiveCourses(): List<CanvasCourse>

    /** Every assignment of one course (complete list, or throws: never a partial list). */
    suspend fun getAssignments(courseId: Long): List<CanvasAssignment>
}

/** What a sync did, for the "Last synced" line and messages. */
data class SyncSummary(
    val courses: Int,
    val tasksAdded: Int,
    val tasksUpdated: Int,
    val tasksMarkedRemoved: Int,
    /** Courses whose assignments couldn't be read this time (their tasks were left untouched). */
    val failedCourses: List<String>,
)

/**
 * One Canvas sync (IDEA.md §3.2–3.3):
 * 1. Read the active courses. Each is matched to a course already in Noteapp: first by Canvas ID,
 *    then by course code (ignoring spaces and case) for a course you created yourself, which
 *    then gets linked to Canvas. Otherwise a new course is created (the app then offers to link
 *    it to a folder).
 * 2. For each course, read ALL its assignments, then apply [CanvasMerge] in one database
 *    transaction: new assignments become tasks, existing tasks get Canvas's changes (your status,
 *    target date and notes are kept), and missing ones are marked "removed from Canvas".
 *    If a course's assignments can't be read, that course is skipped and nothing in it changes.
 *
 * Nothing is ever deleted, and courses that are no longer active are simply not touched.
 * If the course list itself can't be read, the whole sync stops before changing anything.
 */
class CanvasSync(
    private val db: NoteAppDatabase,
    private val source: CanvasSource,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val courseDao = db.courseDao()
    private val taskDao = db.taskDao()

    suspend fun sync(): SyncSummary {
        val canvasCourses = source.getActiveCourses() // throws → nothing changed

        var added = 0
        var updated = 0
        var removed = 0
        val failed = mutableListOf<String>()

        for (canvasCourse in canvasCourses) {
            val course = matchOrCreateCourse(canvasCourse)
            val assignments = try {
                source.getAssignments(canvasCourse.id)
            } catch (e: CanvasException) {
                if (e is CanvasException.SessionExpired || e is CanvasException.NotSignedIn) throw e
                failed += course.code
                continue // skip this course: never mark anything removed after a failed read
            }

            db.withTransaction {
                val now = clock.instant()
                for (assignment in assignments) {
                    val existing = taskDao.getByCanvasAssignmentId(assignment.id)
                    if (existing == null) {
                        taskDao.insert(CanvasMerge.newTask(assignment, course.id, newId(), now))
                        added++
                    } else {
                        val merged = CanvasMerge.update(existing, assignment, course.id, now)
                        if (merged !== existing) {
                            taskDao.update(merged)
                            updated++
                        }
                    }
                }
                val courseTasks = taskDao.getForCourseAndSource(course.id, TaskSource.CANVAS)
                val gone = CanvasMerge.markRemoved(courseTasks, assignments.map { it.id }.toSet(), now)
                gone.forEach { taskDao.update(it) }
                removed += gone.size
            }
        }
        return SyncSummary(canvasCourses.size, added, updated, removed, failed)
    }

    private suspend fun matchOrCreateCourse(canvas: CanvasCourse): Course = db.withTransaction {
        val now = clock.instant()
        val code = CourseCodes.tidy(canvas.courseCode) ?: canvas.name

        courseDao.getByCanvasId(canvas.id)?.let { known ->
            val refreshed = known.copy(code = code, name = canvas.name)
            if (refreshed != known) courseDao.update(refreshed.copy(updatedAt = now))
            return@withTransaction refreshed
        }

        val key = CourseCodes.matchKey(code)
        courseDao.getAll()
            .firstOrNull { it.canvasCourseId == null && CourseCodes.matchKey(it.code) == key }
            ?.let { yours ->
                val linked = yours.copy(canvasCourseId = canvas.id, name = canvas.name, updatedAt = now)
                courseDao.update(linked)
                return@withTransaction linked
            }

        Course(id = newId(), code = code, name = canvas.name, canvasCourseId = canvas.id, createdAt = now, updatedAt = now)
            .also { courseDao.insert(it) }
    }
}
