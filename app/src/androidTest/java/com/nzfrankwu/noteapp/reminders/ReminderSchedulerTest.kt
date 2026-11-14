package com.nzfrankwu.noteapp.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Real alarms on the emulator: set, moved and cancelled as tasks change. */
@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private val nz = ZoneId.of("Pacific/Auckland")
    private val now = Instant.parse("2026-11-14T10:00:00Z")
    private lateinit var scheduler: ReminderScheduler

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scheduler = ReminderScheduler(context, Clock.fixed(now, nz), { nz }, storeName = "reminders-test")
        scheduler.apply(emptyList()) // start clean
    }

    @After
    fun tearDown() = scheduler.apply(emptyList())

    private fun task(id: String, dueAt: Instant?, status: TaskStatus = TaskStatus.NOT_STARTED) = TaskWithCourse(
        Task(
            id = id, title = "Lab", courseId = null, dueAt = dueAt, url = null, descriptionHtml = null,
            status = status, targetAt = null, notes = null, source = TaskSource.MANUAL, canvasAssignmentId = null,
            canvasSubmissionState = null, canvasSubmittedAt = null, removedFromCanvas = false,
            createdAt = now, updatedAt = now,
        ),
        courseCode = null,
    )

    private val inThreeDays = now.plusSeconds(3 * 24 * 3600)

    @Test
    fun aDatedTask_getsBothAlarms() {
        scheduler.apply(listOf(task("test-a", inThreeDays)))
        assertTrue(scheduler.isScheduled("test-a/1d"))
        assertTrue(scheduler.isScheduled("test-a/2h"))
    }

    @Test
    fun finishingTheTask_cancelsItsAlarms() {
        scheduler.apply(listOf(task("test-b", inThreeDays)))
        scheduler.apply(listOf(task("test-b", inThreeDays, status = TaskStatus.DONE)))
        assertFalse(scheduler.isScheduled("test-b/1d"))
        assertFalse(scheduler.isScheduled("test-b/2h"))
    }

    @Test
    fun deletingTheTask_cancelsItsAlarms_butKeepsOthers() {
        scheduler.apply(listOf(task("test-c", inThreeDays), task("test-d", inThreeDays)))
        scheduler.apply(listOf(task("test-d", inThreeDays)))
        assertFalse(scheduler.isScheduled("test-c/1d"))
        assertTrue(scheduler.isScheduled("test-d/1d"))
    }

    @Test
    fun undatedTask_getsNoAlarms() {
        scheduler.apply(listOf(task("test-e", dueAt = null)))
        assertFalse(scheduler.isScheduled("test-e/1d"))
        assertFalse(scheduler.isScheduled("test-e/2h"))
    }
}
