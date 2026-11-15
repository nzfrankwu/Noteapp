package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarRulesTest {
    private val nz = ZoneId.of("Pacific/Auckland")

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(nz).toInstant()

    private data class T(
        override val title: String,
        override val dueAt: Instant? = null,
        override val targetAt: Instant? = null,
        override val status: TaskStatus = TaskStatus.NOT_STARTED,
    ) : TaskLike

    // ---- Weeks ----

    @Test
    fun november2026_runsFromMonday26October_toSunday6December() {
        val weeks = CalendarRules.weeks(YearMonth.of(2026, 11))
        assertEquals(6, weeks.size) // 1 Nov is a Sunday, so the month touches 6 weeks
        assertEquals(LocalDate.of(2026, 10, 26), weeks.first().first())
        assertEquals(LocalDate.of(2026, 12, 6), weeks.last().last())
        assertTrue(weeks.all { it.size == 7 && it.first().dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun february2027_fitsInFourWeeks() {
        // 1 Feb 2027 is a Monday and February has 28 days: exactly 4 weeks.
        val weeks = CalendarRules.weeks(YearMonth.of(2027, 2))
        assertEquals(4, weeks.size)
        assertEquals(LocalDate.of(2027, 2, 1), weeks.first().first())
        assertEquals(LocalDate.of(2027, 2, 28), weeks.last().last())
    }

    @Test
    fun weeks_haveNoGapsOrRepeats() {
        val days = CalendarRules.weeks(YearMonth.of(2026, 9)).flatten()
        days.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
    }

    // ---- Which day a task sits on ----

    @Test
    fun task_sitsOnItsDueDate_evenWithATargetDate() {
        val entry = CalendarRules.entryFor(T("Lab", dueAt = local("2026-11-20T23:59"), targetAt = local("2026-11-18T09:00")), nz)!!
        assertEquals(LocalDate.of(2026, 11, 20), entry.date)
        assertEquals(LocalTime.of(23, 59), entry.time)
        assertFalse(entry.isTarget)
    }

    @Test
    fun taskWithOnlyATargetDate_sitsThere_markedTarget() {
        val entry = CalendarRules.entryFor(T("Read", targetAt = local("2026-11-18T09:00")), nz)!!
        assertEquals(LocalDate.of(2026, 11, 18), entry.date)
        assertTrue(entry.isTarget)
    }

    @Test
    fun undatedTask_isNotOnTheCalendar() {
        assertNull(CalendarRules.entryFor(T("Someday"), nz))
    }

    @Test
    fun dueJustAfterMidnightUtc_usesTheLocalDay() {
        // 11:59 pm Friday in Auckland is still Friday morning in UTC: it must sit on Friday here.
        val entry = CalendarRules.entryFor(T("Lab", dueAt = local("2026-11-20T23:59")), nz)!!
        assertEquals(LocalDate.of(2026, 11, 20), entry.date)
    }

    // ---- Days ----

    @Test
    fun byDay_sortsByTimeThenTitle_andSkipsFinishedUnlessAsked() {
        val tasks = listOf(
            T("b quiz", dueAt = local("2026-11-20T10:00")),
            T("A lab", dueAt = local("2026-11-20T10:00")),
            T("early", dueAt = local("2026-11-20T08:00")),
            T("done one", dueAt = local("2026-11-20T09:00"), status = TaskStatus.DONE),
            T("other day", dueAt = local("2026-11-21T09:00")),
        )
        val from = LocalDate.of(2026, 11, 16)
        val to = LocalDate.of(2026, 11, 22)
        val day = LocalDate.of(2026, 11, 20)

        val open = CalendarRules.byDay(tasks, nz, from, to, includeFinished = false)
        assertEquals(listOf("early", "A lab", "b quiz"), open.getValue(day).map { it.task.title })

        val all = CalendarRules.byDay(tasks, nz, from, to, includeFinished = true)
        assertEquals(listOf("early", "done one", "A lab", "b quiz"), all.getValue(day).map { it.task.title })
    }

    @Test
    fun byDay_onlyIncludesTheShownDays() {
        val tasks = listOf(T("before", dueAt = local("2026-10-25T12:00")), T("inside", dueAt = local("2026-10-26T12:00")))
        val days = CalendarRules.byDay(tasks, nz, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 12, 6), includeFinished = false)
        assertEquals(setOf(LocalDate.of(2026, 10, 26)), days.keys)
    }

    // ---- "+N more" ----

    @Test
    fun chipsShown_allWhenTheyFit_upToThree() {
        assertEquals(0, CalendarRules.chipsShown(total = 0, fit = 3))
        assertEquals(2, CalendarRules.chipsShown(total = 2, fit = 5))
        assertEquals(3, CalendarRules.chipsShown(total = 3, fit = 3))
    }

    @Test
    fun chipsShown_leavesRoomForMore() {
        assertEquals(3, CalendarRules.chipsShown(total = 5, fit = 10)) // 3 + "+2 more"
        assertEquals(3, CalendarRules.chipsShown(total = 4, fit = 4)) // 3 + "+1 more"
        assertEquals(1, CalendarRules.chipsShown(total = 3, fit = 2)) // 1 + "+2 more"
        assertEquals(0, CalendarRules.chipsShown(total = 2, fit = 1)) // just "+2 more"
        assertEquals(0, CalendarRules.chipsShown(total = 2, fit = 0))
    }
}
