package com.nzfrankwu.noteapp.data

import com.nzfrankwu.noteapp.data.db.Folder
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.PageTemplate
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LinkChoicesTest {
    private val t0 = Instant.parse("2026-11-16T00:00:00Z")

    private fun folder(id: String, name: String, parent: String? = null, course: String? = null) =
        Folder(id = id, name = name, courseId = course, createdAt = t0, updatedAt = t0, parentId = parent)

    private fun notebook(id: String, folder: String, deleted: Boolean = false) =
        Notebook(id, folder, id, PageTemplate.LINED, 595f, 842f, t0, t0, if (deleted) t0 else null)

    private fun task(id: String, course: String?, due: Instant? = null, status: TaskStatus = TaskStatus.NOT_STARTED, archived: Boolean = false) =
        TaskWithCourse(
            Task(
                id = id, title = id, courseId = course, dueAt = due, url = null, descriptionHtml = null, status = status,
                targetAt = null, notes = null, source = TaskSource.MANUAL, canvasAssignmentId = null,
                canvasSubmissionState = null, canvasSubmittedAt = null, removedFromCanvas = false, createdAt = t0, updatedAt = t0,
            ),
            courseCode = course,
            courseArchivedAt = if (archived) t0 else null,
        )

    private val folders = listOf(
        folder("f1", "ELECTENG 204", course = "c1"),
        folder("s1", "Tutorials", parent = "f1"),
        folder("s2", "Deep", parent = "s1"),
        folder("f2", "Maths", course = "c2"),
        folder("s3", "Labs", parent = "f2"),
    )

    @Test
    fun courseFolderAndItsSubfolders_comeFirst() {
        val nbs = listOf(notebook("a", "f1"), notebook("b", "s2"), notebook("c", "f2"), notebook("d", "s3"))
        val choices = LinkChoices.notebooks("f1", folders, nbs, exclude = emptySet())
        assertEquals(listOf("f1", "s2"), choices.inCourse.map { it.folder.folder.id })
        assertEquals(listOf(0, 2), choices.inCourse.map { it.folder.depth })
        assertEquals(listOf("f2", "s3"), choices.others.map { it.folder.folder.id })
    }

    @Test
    fun linkedAndDeletedNotebooks_areLeftOut() {
        val nbs = listOf(notebook("a", "f1"), notebook("b", "f1", deleted = true), notebook("c", "f1"))
        val choices = LinkChoices.notebooks("f1", folders, nbs, exclude = setOf("a"))
        assertEquals(listOf("c"), choices.inCourse.single().notebooks.map { it.id })
    }

    @Test
    fun noCourseFolder_putsEverythingInOthers() {
        val choices = LinkChoices.notebooks(null, folders, listOf(notebook("a", "f1"), notebook("c", "s3")), emptySet())
        assertTrue(choices.inCourse.isEmpty())
        assertEquals(2, choices.others.size)
    }

    @Test
    fun aSubfolderAsTheCourseFolder_takesOnlyItsOwnBranch() {
        val choices = LinkChoices.notebooks("s1", folders, listOf(notebook("a", "f1"), notebook("b", "s2")), emptySet())
        assertEquals(listOf("s2"), choices.inCourse.map { it.folder.folder.id })
        assertEquals(1, choices.inCourse.single().folder.depth)
        assertEquals(listOf("f1"), choices.others.map { it.folder.folder.id })
    }

    @Test
    fun tasks_courseFirst_unfinishedByDate_thenFinished_noArchived() {
        val tasks = listOf(
            task("late", "c1", due = t0.plusSeconds(900)),
            task("soon", "c1", due = t0.plusSeconds(100)),
            task("undated", "c1"),
            task("done", "c1", due = t0, status = TaskStatus.DONE),
            task("other", "c2", due = t0),
            task("old", "c3", due = t0, archived = true),
            task("linked", "c1", due = t0),
        )
        val choices = LinkChoices.tasks("c1", tasks, exclude = setOf("linked"))
        assertEquals(listOf("soon", "late", "undated", "done"), choices.inCourse.map { it.task.id })
        assertEquals(listOf("other"), choices.others.map { it.task.id })
    }

    // ---- Search and filters (PHASE-5.md FB3) ----

    private val now = t0.plusSeconds(500)
    private val pickable = LinkChoices.tasks(
        "c1",
        listOf(
            task("Lab 3 report", "c1", due = t0.plusSeconds(900)),
            task("Tutorial 5", "c1", due = t0.plusSeconds(100)), // already due
            task("Reading", "c1"),
            task("Quiz 2", "c1", due = t0, status = TaskStatus.DONE), // done and past
            task("Assignment 1", "c2", due = t0.plusSeconds(2000)),
            task("Tutorial 9", "c2", due = t0.plusSeconds(200)),
            task("Essay", "c3", due = t0.plusSeconds(800)),
        ),
        emptySet(),
    )

    private fun ids(filter: TaskFilter) = LinkChoices.filter(pickable, filter, now).map { it.task.id }

    @Test
    fun theDefault_isThisCourseDueSoon_withUndatedTasks() {
        assertEquals(listOf("Lab 3 report", "Reading"), ids(TaskFilter()))
    }

    @Test
    fun alreadyDue_isPastDueDates_latestFirst_doneIncluded() {
        assertEquals(listOf("Tutorial 5", "Quiz 2"), ids(TaskFilter(due = DueFilter.PAST)))
    }

    @Test
    fun otherCourses_andOneCourse() {
        assertEquals(listOf("Essay", "Assignment 1"), ids(TaskFilter(course = CourseFilter.OtherCourses)))
        assertEquals(listOf("Tutorial 9"), ids(TaskFilter(course = CourseFilter.OtherCourses, due = DueFilter.PAST)))
        assertEquals(listOf("Assignment 1"), ids(TaskFilter(course = CourseFilter.One("c2", "c2"))))
        assertEquals(listOf("c2", "c3"), LinkChoices.courses(pickable).map { it.courseId })
    }

    @Test
    fun search_matchesEveryWord_inTitleOrCourseCode_anyCase() {
        assertEquals(listOf("Lab 3 report"), ids(TaskFilter(query = "  REPORT lab ")))
        assertEquals(listOf("Essay"), ids(TaskFilter(query = "c3", course = CourseFilter.OtherCourses)))
        assertEquals(emptyList<String>(), ids(TaskFilter(query = "tutorial"))) // only past ones match
        assertEquals(listOf("Tutorial 5"), ids(TaskFilter(query = "tutorial", due = DueFilter.PAST)))
    }

    @Test
    fun withoutACourse_thisCourseAndOtherCourses_bothShowEverything() {
        val noCourse = LinkChoices.tasks(null, pickable.all, emptySet())
        val soon = LinkChoices.filter(noCourse, TaskFilter(), now).map { it.task.id }.toSet()
        assertEquals(setOf("Lab 3 report", "Reading", "Assignment 1", "Essay"), soon)
        assertEquals(soon, LinkChoices.filter(noCourse, TaskFilter(course = CourseFilter.OtherCourses), now).map { it.task.id }.toSet())
    }

    @Test
    fun tasks_withoutACourse_areAllOthers() {
        val choices = LinkChoices.tasks(null, listOf(task("a", "c1"), task("b", null)), emptySet())
        assertTrue(choices.inCourse.isEmpty())
        assertEquals(2, choices.others.size)
    }
}
