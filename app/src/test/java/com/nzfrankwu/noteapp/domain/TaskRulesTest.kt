package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TaskRulesTest {
    private val nz = ZoneId.of("Pacific/Auckland")

    /** Wednesday 18 November 2026, 10:00 am in Auckland (NZDT, UTC+13). */
    private val now: Instant = local("2026-11-18T10:00")

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(nz).toInstant()

    private data class T(
        override val title: String,
        override val dueAt: Instant? = null,
        override val targetAt: Instant? = null,
        override val status: TaskStatus = TaskStatus.NOT_STARTED,
    ) : TaskLike

    // ---- Overdue and past target ----

    @Test
    fun dueDatePassed_isOverdue() {
        assertTrue(TaskRules.isOverdue(T("Lab", dueAt = local("2026-11-18T09:59")), now))
    }

    @Test
    fun dueDateNotPassed_isNotOverdue() {
        assertFalse(TaskRules.isOverdue(T("Lab", dueAt = local("2026-11-18T10:01")), now))
    }

    @Test
    fun submittedOrDone_isNeverOverdue() {
        val late = local("2026-11-01T12:00")
        assertFalse(TaskRules.isOverdue(T("Lab", dueAt = late, status = TaskStatus.SUBMITTED), now))
        assertFalse(TaskRules.isOverdue(T("Lab", dueAt = late, status = TaskStatus.DONE), now))
    }

    @Test
    fun stuckTask_canStillBeOverdue() {
        assertTrue(TaskRules.isOverdue(T("Lab", dueAt = local("2026-11-17T12:00"), status = TaskStatus.STUCK), now))
    }

    @Test
    fun noDueDate_isNeverOverdue() {
        assertFalse(TaskRules.isOverdue(T("Read chapter 3", targetAt = local("2026-11-01T09:00")), now))
    }

    @Test
    fun missedTargetDate_isPastTarget_notOverdue() {
        val task = T("Assignment", dueAt = local("2026-11-25T23:59"), targetAt = local("2026-11-17T09:00"))
        assertFalse(TaskRules.isOverdue(task, now))
        assertTrue(TaskRules.isPastTarget(task, now))
    }

    @Test
    fun overdueTask_isNotAlsoPastTarget() {
        val task = T("Assignment", dueAt = local("2026-11-17T23:59"), targetAt = local("2026-11-16T09:00"))
        assertTrue(TaskRules.isOverdue(task, now))
        assertFalse(TaskRules.isPastTarget(task, now))
    }

    @Test
    fun effectiveDate_prefersTargetDate() {
        val target = local("2026-11-20T09:00")
        assertEquals(target, TaskRules.effectiveDate(T("A", dueAt = local("2026-11-30T23:59"), targetAt = target)))
        assertEquals(local("2026-11-30T23:59"), TaskRules.effectiveDate(T("A", dueAt = local("2026-11-30T23:59"))))
    }

    // ---- Default times, including daylight saving ----

    @Test
    fun dueDateWithoutTime_meansEndOfDay() {
        assertEquals(local("2026-11-20T23:59"), TaskDates.dueAt(LocalDate.parse("2026-11-20"), null, nz))
    }

    @Test
    fun targetDateWithoutTime_means9am() {
        assertEquals(local("2026-11-20T09:00"), TaskDates.targetAt(LocalDate.parse("2026-11-20"), null, nz))
    }

    @Test
    fun pickedTime_isUsed() {
        assertEquals(local("2026-11-20T14:30"), TaskDates.dueAt(LocalDate.parse("2026-11-20"), LocalTime.of(14, 30), nz))
    }

    @Test
    fun daylightSavingStartDay_givesTheRightInstants() {
        // NZ clocks go forward on Sunday 27 Sept 2026 (UTC+12 → UTC+13).
        val day = LocalDate.parse("2026-09-27")
        assertEquals(Instant.parse("2026-09-27T10:59:00Z"), TaskDates.dueAt(day, null, nz)) // 11:59 pm NZDT
        assertEquals(Instant.parse("2026-09-26T20:00:00Z"), TaskDates.targetAt(day, null, nz)) // 9:00 am NZDT
    }

    @Test
    fun daylightSavingEndDay_givesTheRightInstants() {
        // NZ clocks go back on Sunday 4 April 2027 (UTC+13 → UTC+12).
        val day = LocalDate.parse("2027-04-04")
        assertEquals(Instant.parse("2027-04-04T11:59:00Z"), TaskDates.dueAt(day, null, nz)) // 11:59 pm NZST
        assertEquals(Instant.parse("2027-04-03T21:00:00Z"), TaskDates.targetAt(day, null, nz)) // 9:00 am NZST
    }

    // ---- Due soon sections ----

    private fun groupsOf(tasks: List<T>, includeFinished: Boolean = false) =
        TaskGrouping.sections(tasks, now, nz, includeFinished).associate { s -> s.group to s.tasks.map { it.title } }

    @Test
    fun tasks_landInTheRightSections() {
        val groups = groupsOf(
            listOf(
                T("overdue", dueAt = local("2026-11-17T23:59")),
                T("today", dueAt = local("2026-11-18T23:59")),
                T("tomorrow", dueAt = local("2026-11-19T12:00")),
                T("friday", dueAt = local("2026-11-20T23:59")),
                T("sunday", dueAt = local("2026-11-22T23:59")),
                T("next monday", dueAt = local("2026-11-23T09:00")),
                T("undated"),
            ),
        )
        assertEquals(listOf("overdue"), groups[DueGroup.OVERDUE])
        assertEquals(listOf("today"), groups[DueGroup.TODAY])
        assertEquals(listOf("tomorrow"), groups[DueGroup.TOMORROW])
        assertEquals(listOf("friday", "sunday"), groups[DueGroup.THIS_WEEK])
        assertEquals(listOf("next monday"), groups[DueGroup.LATER])
        assertEquals(listOf("undated"), groups[DueGroup.NO_DUE_DATE])
    }

    @Test
    fun sections_comeInDisplayOrder_andEmptyOnesAreLeftOut() {
        val sections = TaskGrouping.sections(
            listOf(T("later", dueAt = local("2026-12-01T09:00")), T("overdue", dueAt = local("2026-11-01T09:00"))),
            now, nz,
        )
        assertEquals(listOf(DueGroup.OVERDUE, DueGroup.LATER), sections.map { it.group })
    }

    @Test
    fun targetDate_decidesTheSection() {
        val groups = groupsOf(listOf(T("assignment", dueAt = local("2026-12-10T23:59"), targetAt = local("2026-11-19T09:00"))))
        assertEquals(listOf("assignment"), groups[DueGroup.TOMORROW])
    }

    @Test
    fun missedTarget_butNotDue_showsUnderToday() {
        val groups = groupsOf(listOf(T("assignment", dueAt = local("2026-12-10T23:59"), targetAt = local("2026-11-16T09:00"))))
        assertEquals(listOf("assignment"), groups[DueGroup.TODAY])
    }

    @Test
    fun undatedTask_withTargetDate_isPlacedByTarget() {
        val groups = groupsOf(listOf(T("study", targetAt = local("2026-11-19T09:00"))))
        assertEquals(listOf("study"), groups[DueGroup.TOMORROW])
    }

    @Test
    fun withinASection_sortedByDateThenTitle() {
        val groups = groupsOf(
            listOf(
                T("b late", dueAt = local("2026-11-23T18:00")),
                T("a early", dueAt = local("2026-11-24T09:00")),
                T("Same time B", dueAt = local("2026-11-25T09:00")),
                T("same time a", dueAt = local("2026-11-25T09:00")),
            ),
        )
        assertEquals(listOf("b late", "a early", "same time a", "Same time B"), groups[DueGroup.LATER])
    }

    @Test
    fun overdue_sortedByDueDate_evenWithTargets() {
        val groups = groupsOf(
            listOf(
                T("due 17th", dueAt = local("2026-11-17T09:00"), targetAt = local("2026-11-10T09:00")),
                T("due 16th", dueAt = local("2026-11-16T09:00")),
            ),
        )
        assertEquals(listOf("due 16th", "due 17th"), groups[DueGroup.OVERDUE])
    }

    @Test
    fun noDueDate_sortedByTitle() {
        val groups = groupsOf(listOf(T("zebra"), T("Apple"), T("mango")))
        assertEquals(listOf("Apple", "mango", "zebra"), groups[DueGroup.NO_DUE_DATE])
    }

    @Test
    fun finishedTasks_areHiddenUnlessAskedFor() {
        val tasks = listOf(
            T("done", dueAt = local("2026-11-19T09:00"), status = TaskStatus.DONE),
            T("submitted", dueAt = local("2026-11-10T09:00"), status = TaskStatus.SUBMITTED),
            T("open", dueAt = local("2026-11-19T09:00")),
        )
        assertEquals(setOf(DueGroup.TOMORROW), groupsOf(tasks).keys)
        assertEquals(listOf("submitted", "done"), groupsOf(tasks, includeFinished = true)[DueGroup.FINISHED])
    }

    @Test
    fun onSunday_thisWeekIsEmpty_andMondayIsTomorrow() {
        val sundayNow = local("2026-11-22T10:00")
        val sections = TaskGrouping.sections(
            listOf(T("monday", dueAt = local("2026-11-23T09:00")), T("tuesday", dueAt = local("2026-11-24T09:00"))),
            sundayNow, nz,
        ).associate { s -> s.group to s.tasks.map { it.title } }
        assertEquals(listOf("monday"), sections[DueGroup.TOMORROW])
        assertEquals(listOf("tuesday"), sections[DueGroup.LATER])
        assertEquals(null, sections[DueGroup.THIS_WEEK])
    }

    @Test
    fun lateEvening_dayBoundaryUsesLocalTime() {
        // 11:30 pm Wed in Auckland is already Wednesday 10:30 UTC; a task due 12:30 am Thursday is "Tomorrow".
        val lateNow = local("2026-11-18T23:30")
        val sections = TaskGrouping.sections(listOf(T("early thu", dueAt = local("2026-11-19T00:30"))), lateNow, nz)
        assertEquals(DueGroup.TOMORROW, sections.single().group)
    }
}
