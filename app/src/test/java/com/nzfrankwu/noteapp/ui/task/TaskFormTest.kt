package com.nzfrankwu.noteapp.ui.task

import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskFormTest {
    private val nz = ZoneId.of("Pacific/Auckland")
    private val day = LocalDate.of(2026, 11, 20)

    private fun at(date: LocalDate, h: Int, m: Int, s: Int = 0): Instant =
        ZonedDateTime.of(date, LocalTime.of(h, m, s), nz).toInstant()

    private fun task(
        dueAt: Instant? = null,
        targetAt: Instant? = null,
        source: TaskSource = TaskSource.MANUAL,
    ) = Task(
        id = "t1", title = "Lab 3", courseId = "c1", dueAt = dueAt, url = "https://example.com",
        descriptionHtml = null, status = TaskStatus.STARTED, targetAt = targetAt, notes = "bring kit",
        source = source, canvasAssignmentId = null, canvasSubmissionState = null, canvasSubmittedAt = null,
        removedFromCanvas = false, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test
    fun dateWithoutTime_usesTheDefaultTimes() {
        val form = TaskForm.new("c1").withDueDate(day).withTargetDate(day.minusDays(2))
        assertEquals(at(day, 23, 59), form.dueAt(nz))
        assertEquals(at(day.minusDays(2), 9, 0), form.targetAt(nz))
    }

    @Test
    fun pickedTime_isUsed() {
        val form = TaskForm.new("c1").withDueDate(day).withDueTime(LocalTime.of(14, 30))
        assertEquals(at(day, 14, 30), form.dueAt(nz))
    }

    @Test
    fun noDate_meansNoInstant_andTimeAloneIsIgnored() {
        val form = TaskForm.new("c1").withDueTime(LocalTime.of(10, 0))
        assertNull(form.dueTime)
        assertNull(form.dueAt(nz))
        assertNull(form.targetAt(nz))
    }

    @Test
    fun clearingADate_alsoClearsItsTime() {
        val form = TaskForm.new("c1").withDueDate(day).withDueTime(LocalTime.NOON).withDueDate(null)
        assertNull(form.dueDate)
        assertNull(form.dueTime)
    }

    @Test
    fun editingWithoutChanges_keepsTheExactSavedInstants() {
        // Canvas often says 11:59:59 pm; the form shows 11:59 pm but must not move the date.
        val due = at(day, 23, 59, 59)
        val target = at(day.minusDays(1), 18, 15, 30)
        val form = TaskForm.from(task(due, target), nz)
        assertEquals(LocalTime.of(23, 59), form.dueTime)
        assertEquals(due, form.dueAt(nz))
        assertEquals(target, form.targetAt(nz))
    }

    @Test
    fun editingTheTime_savesTheNewTime() {
        val form = TaskForm.from(task(at(day, 23, 59, 59)), nz).withDueTime(LocalTime.of(17, 0))
        assertEquals(at(day, 17, 0), form.dueAt(nz))
    }

    @Test
    fun formFromTask_carriesEveryField() {
        val form = TaskForm.from(task(source = TaskSource.CANVAS), nz)
        assertEquals("Lab 3", form.title)
        assertEquals("c1", form.courseId)
        assertEquals("https://example.com", form.url)
        assertEquals("bring kit", form.notes)
        assertTrue(form.isCanvas)
    }

    @Test
    fun nzDaylightSavingStart_isHandled() {
        // 27 Sep 2026: clocks jump from 2:00 to 3:00 am in NZ. A 9:00 am target is unaffected.
        val dstDay = LocalDate.of(2026, 9, 27)
        val form = TaskForm.new("c1").withTargetDate(dstDay)
        assertEquals(Instant.parse("2026-09-26T20:00:00Z"), form.targetAt(nz))
    }

    @Test
    fun canSave_needsTitleAndCourse_exceptForCanvasTasks() {
        assertFalse(TaskForm.new("c1").canSave)
        assertFalse(TaskForm.new(null, title = "Study").canSave)
        assertTrue(TaskForm.new("c1", title = "Study").canSave)
        assertTrue(TaskForm(isCanvas = true).canSave)
    }

    @Test
    fun prefill_fromASharedLink() {
        val form = TaskForm.new(title = "Assignment 2", url = "https://canvas.example.edu/courses/5/assignments/9")
        assertEquals("Assignment 2", form.title)
        assertEquals("https://canvas.example.edu/courses/5/assignments/9", form.url)
        assertNull(form.courseId)
    }
}
