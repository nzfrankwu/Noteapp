package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DaysLeftTest {
    private val nz = ZoneId.of("Pacific/Auckland")

    /** Wednesday 18 November 2026, 10:00 am in Auckland. */
    private val now: Instant = local("2026-11-18T10:00")

    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(nz).toInstant()

    private data class T(
        override val title: String = "Lab",
        override val dueAt: Instant? = null,
        override val targetAt: Instant? = null,
        override val status: TaskStatus = TaskStatus.NOT_STARTED,
    ) : TaskLike

    private fun daysLeft(due: String, at: Instant = now, status: TaskStatus = TaskStatus.NOT_STARTED) =
        DaysLeft.of(T(dueAt = local(due), status = status), at, nz)

    // ---- Wording ----

    @Test
    fun dueLaterToday_isDueToday() {
        val d = daysLeft("2026-11-18T23:59")!!
        assertEquals("Due today", d.text)
        assertEquals(DaysLeftUrgency.SOON, d.urgency)
    }

    @Test
    fun dueTomorrow_evenEarlyInTheMorning() {
        assertEquals("Due tomorrow", daysLeft("2026-11-19T00:05")!!.text)
        assertEquals("Due tomorrow", daysLeft("2026-11-19T23:59")!!.text)
    }

    @Test
    fun twoDaysLeft_isStillSoon() {
        val d = daysLeft("2026-11-20T09:00")!!
        assertEquals("2 days left", d.text)
        assertEquals(DaysLeftUrgency.SOON, d.urgency)
    }

    @Test
    fun threeDaysLeft_isNormal() {
        val d = daysLeft("2026-11-21T23:59")!!
        assertEquals("3 days left", d.text)
        assertEquals(DaysLeftUrgency.NORMAL, d.urgency)
    }

    @Test
    fun countsCalendarDays_notHours() {
        // 11:30 pm tonight to 12:30 am the day after tomorrow is only 25 hours, but 2 calendar days.
        val lateNight = local("2026-11-18T23:30")
        assertEquals("2 days left", daysLeft("2026-11-20T00:30", at = lateNight)!!.text)
    }

    @Test
    fun dueEarlierToday_wasDueToday_andOverdue() {
        val d = daysLeft("2026-11-18T09:00")!!
        assertEquals("Was due today", d.text)
        assertEquals(DaysLeftUrgency.OVERDUE, d.urgency)
    }

    @Test
    fun dueYesterday_isOneDayOverdue() {
        val d = daysLeft("2026-11-17T23:59")!!
        assertEquals("1 day overdue", d.text)
        assertEquals(DaysLeftUrgency.OVERDUE, d.urgency)
    }

    @Test
    fun dueFiveDaysAgo_isFiveDaysOverdue() {
        assertEquals("5 days overdue", daysLeft("2026-11-13T12:00")!!.text)
    }

    @Test
    fun farAway_countsAllDays_acrossTheYearEnd() {
        assertEquals("44 days left", daysLeft("2027-01-01T09:00")!!.text)
    }

    // ---- When nothing is shown ----

    @Test
    fun noDueDate_showsNothing_evenWithATargetDate() {
        assertNull(DaysLeft.of(T(targetAt = local("2026-11-19T09:00")), now, nz))
    }

    @Test
    fun finishedTasks_showNothing() {
        assertNull(daysLeft("2026-11-20T23:59", status = TaskStatus.DONE))
        assertNull(daysLeft("2026-11-10T23:59", status = TaskStatus.DONE))
    }

    @Test
    fun targetDate_neverChangesTheCount() {
        val task = T(dueAt = local("2026-11-25T23:59"), targetAt = local("2026-11-17T09:00"))
        assertEquals("7 days left", DaysLeft.of(task, now, nz)!!.text)
    }

    @Test
    fun stuckTask_stillCounts() {
        assertEquals("1 day overdue", daysLeft("2026-11-17T12:00", status = TaskStatus.STUCK)!!.text)
    }

    // ---- Daylight saving (NZ) ----

    @Test
    fun daylightSavingStarts_23HourDayStillCountsAsOneDay() {
        // Clocks jump from 2:00 to 3:00 am on Sunday 27 September 2026. From 12:30 am that Sunday
        // to midnight on Monday is only 22.5 hours, but Monday is tomorrow.
        val sundayEarly = local("2026-09-27T00:30")
        assertEquals("Due tomorrow", daysLeft("2026-09-28T00:00", at = sundayEarly)!!.text)
        assertEquals("2 days left", daysLeft("2026-09-29T00:00", at = sundayEarly)!!.text)
    }

    @Test
    fun daylightSavingEnds_25HourDayStillCountsAsOneDay() {
        // Clocks go back from 3:00 to 2:00 am on Sunday 5 April 2026. Saturday 11:30 pm to
        // Monday 12:10 am is almost 26 hours, which is still 2 calendar days.
        val saturdayNight = local("2026-04-04T23:30")
        assertEquals("2 days left", daysLeft("2026-04-06T00:10", at = saturdayNight)!!.text)
        assertEquals("Due tomorrow", daysLeft("2026-04-05T23:59", at = saturdayNight)!!.text)
    }

    @Test
    fun usesTheGivenTimeZone() {
        // 10:00 am Wed in Auckland is still Tuesday in London, so a Wednesday 11 pm (NZ) due date
        // is "tomorrow" there.
        val task = T(dueAt = local("2026-11-18T23:59"))
        assertEquals("Due today", DaysLeft.of(task, now, nz)!!.text)
        assertEquals("Due tomorrow", DaysLeft.of(task, now, ZoneId.of("Europe/London"))!!.text)
    }
}
