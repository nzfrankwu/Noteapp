package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class RemindersTest {
    private val nz = ZoneId.of("Pacific/Auckland")
    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(nz).toInstant()
    private val now = local("2026-11-18T10:00")

    private data class T(
        override val title: String = "Assignment 3",
        override val dueAt: Instant? = null,
        override val targetAt: Instant? = null,
        override val status: TaskStatus = TaskStatus.NOT_STARTED,
    ) : TaskLike

    private fun plan(task: T, at: Instant = now) = ReminderPlanner.plan("task-1", task, at, nz)

    @Test
    fun dueTask_getsDayBeforeAndTwoHoursBefore() {
        val reminders = plan(T(dueAt = local("2026-11-21T23:59")))
        assertEquals(
            listOf(
                PlannedReminder("task-1", ReminderOffset.DAY_BEFORE, local("2026-11-20T23:59")),
                PlannedReminder("task-1", ReminderOffset.TWO_HOURS_BEFORE, local("2026-11-21T21:59")),
            ),
            reminders,
        )
    }

    @Test
    fun targetDate_isUsedInsteadOfDueDate() {
        val reminders = plan(T(dueAt = local("2026-11-30T23:59"), targetAt = local("2026-11-21T09:00")))
        assertEquals(local("2026-11-20T09:00"), reminders.first { it.offset == ReminderOffset.DAY_BEFORE }.at)
        assertEquals(local("2026-11-21T07:00"), reminders.first { it.offset == ReminderOffset.TWO_HOURS_BEFORE }.at)
    }

    @Test
    fun finishedTasks_getNoReminders() {
        assertTrue(plan(T(dueAt = local("2026-11-21T23:59"), status = TaskStatus.DONE)).isEmpty())
        assertTrue(plan(T(dueAt = local("2026-11-21T23:59"), status = TaskStatus.DONE)).isEmpty())
    }

    @Test
    fun undatedTasks_getNoReminders() {
        assertTrue(plan(T()).isEmpty())
    }

    @Test
    fun pastReminders_areDropped() {
        // Due in 3 hours: "1 day before" has passed, "2 hours before" is still ahead.
        val reminders = plan(T(dueAt = local("2026-11-18T13:00")))
        assertEquals(listOf(ReminderOffset.TWO_HOURS_BEFORE), reminders.map { it.offset })
        assertTrue(plan(T(dueAt = local("2026-11-18T11:00"))).isEmpty())
    }

    @Test
    fun reminderIds_areStablePerTaskAndOffset() {
        val ids = plan(T(dueAt = local("2026-11-21T23:59"))).map { it.id }
        assertEquals(listOf("task-1/1d", "task-1/2h"), ids)
    }

    @Test
    fun dayBefore_keepsTheClockTime_acrossDaylightSavingStart() {
        // Due 11:59 pm on Sun 27 Sept 2026, the day NZ clocks go forward.
        val due = local("2026-09-27T23:59")
        val dayBefore = ReminderPlanner.plan("t", T(dueAt = due), local("2026-09-20T09:00"), nz).first().at
        assertEquals(LocalDateTime.parse("2026-09-26T23:59"), LocalDateTime.ofInstant(dayBefore, nz))
        assertEquals("Only 23 real hours that day", 23L, java.time.Duration.between(dayBefore, due).toHours())
    }

    @Test
    fun dayBefore_keepsTheClockTime_acrossDaylightSavingEnd() {
        // Due 11:59 pm on Sun 4 April 2027, the day NZ clocks go back.
        val due = local("2027-04-04T23:59")
        val dayBefore = ReminderPlanner.plan("t", T(dueAt = due), local("2027-03-30T09:00"), nz).first().at
        assertEquals(LocalDateTime.parse("2027-04-03T23:59"), LocalDateTime.ofInstant(dayBefore, nz))
        assertEquals(25L, java.time.Duration.between(dayBefore, due).toHours())
    }

    @Test
    fun messages_sayWhatAndWhen() {
        val due = T(dueAt = local("2026-11-21T23:59"))
        assertEquals("Due tomorrow at 11:59 pm · ELECTENG 204", ReminderPlanner.message(due, ReminderOffset.DAY_BEFORE, "ELECTENG 204", nz))
        assertEquals("Due in 2 hours at 11:59 pm", ReminderPlanner.message(due, ReminderOffset.TWO_HOURS_BEFORE, null, nz))
        val target = T(dueAt = local("2026-11-30T23:59"), targetAt = local("2026-11-21T09:00"))
        assertEquals("Your target is tomorrow at 9:00 am", ReminderPlanner.message(target, ReminderOffset.DAY_BEFORE, null, nz))
    }
}
