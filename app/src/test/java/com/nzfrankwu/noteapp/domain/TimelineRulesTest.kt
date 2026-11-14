package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TimelineRulesTest {
    private val nz = ZoneId.of("Pacific/Auckland")
    private fun local(text: String): Instant = LocalDateTime.parse(text).atZone(nz).toInstant()

    private val sunday15 = LocalDate.of(2026, 11, 15)
    private val wednesday18 = LocalDate.of(2026, 11, 18)

    // ---- Which days show ----

    @Test
    fun twoWeeks_startOnMondayThisWeek() {
        assertEquals(LocalDate.of(2026, 11, 9), TimelineRules.defaultStart(sunday15, TimelineZoom.TWO_WEEKS))
        assertEquals(LocalDate.of(2026, 11, 16), TimelineRules.defaultStart(wednesday18, TimelineZoom.TWO_WEEKS))
        assertEquals(14, TimelineRules.days(LocalDate.of(2026, 11, 16), TimelineZoom.TWO_WEEKS).size)
    }

    @Test
    fun month_startsOnMondayLastWeek_andShowsFiveWeeks() {
        val start = TimelineRules.defaultStart(wednesday18, TimelineZoom.MONTH)
        assertEquals(LocalDate.of(2026, 11, 9), start)
        val days = TimelineRules.days(start, TimelineZoom.MONTH)
        assertEquals(35, days.size)
        assertEquals(LocalDate.of(2026, 12, 13), days.last())
    }

    @Test
    fun arrows_moveAWeek_orFourWeeks() {
        val start = LocalDate.of(2026, 11, 16)
        assertEquals(LocalDate.of(2026, 11, 23), TimelineRules.shift(start, TimelineZoom.TWO_WEEKS, +1))
        assertEquals(LocalDate.of(2026, 11, 9), TimelineRules.shift(start, TimelineZoom.TWO_WEEKS, -1))
        assertEquals(LocalDate.of(2026, 12, 14), TimelineRules.shift(start, TimelineZoom.MONTH, +1))
    }

    // ---- Bars ----

    @Test
    fun barStartsWhenCanvasMakesItAvailable_orWhenItWasAdded() {
        val due = local("2026-11-20T23:59")
        assertEquals(local("2026-11-06T09:00"), TimelineRules.barStart(local("2026-11-06T09:00"), local("2026-11-15T10:00"), due))
        assertEquals(local("2026-11-15T10:00"), TimelineRules.barStart(null, local("2026-11-15T10:00"), due))
    }

    @Test
    fun barNeverStartsAfterItsDueDate() {
        val due = local("2026-11-20T23:59")
        assertEquals(due, TimelineRules.barStart(null, local("2026-11-25T10:00"), due))
    }

    @Test
    fun position_countsDaysAndFractions_inLocalTime() {
        val first = LocalDate.of(2026, 11, 16)
        assertEquals(0.0, TimelineRules.position(local("2026-11-16T00:00"), first, nz), 1e-9)
        assertEquals(4.5, TimelineRules.position(local("2026-11-20T12:00"), first, nz), 1e-9)
        assertEquals(-1.0, TimelineRules.position(local("2026-11-15T00:00"), first, nz), 1e-9)
    }

    @Test
    fun position_onADaylightSavingDay_stillFillsTheDay() {
        // Sunday 27 September 2026 has only 23 hours in Auckland; its midday sits 12/23 into the day.
        val first = LocalDate.of(2026, 9, 21)
        val noon = TimelineRules.position(local("2026-09-27T12:00"), first, nz)
        assertEquals(6 + 11.0 / 23, noon, 1e-9) // 2 am → 3 am is skipped, so noon is 11 hours in
        assertEquals(7.0, TimelineRules.position(local("2026-09-28T00:00"), first, nz), 1e-9)
    }

    @Test
    fun placement_visible_cutToTheVisibleDays() {
        val p = TimelineRules.placement(-3.0, 4.9, 14) as TimelinePlacement.Visible
        assertEquals(0.0, p.start, 1e-9)
        assertEquals(4.9, p.end, 1e-9)
        assertTrue(p.startsEarlier)
        assertTrue(!p.endsLater)
    }

    @Test
    fun placement_carriesOnPastTheRightEdge() {
        val p = TimelineRules.placement(10.0, 20.0, 14) as TimelinePlacement.Visible
        assertEquals(14.0, p.end, 1e-9)
        assertTrue(p.endsLater)
    }

    @Test
    fun placement_offScreen_onEitherSide() {
        assertEquals(TimelinePlacement.Before, TimelineRules.placement(-9.0, -2.0, 14))
        assertEquals(TimelinePlacement.After, TimelineRules.placement(15.0, 20.0, 14))
    }

    @Test
    fun placement_aVeryShortBar_isStillDrawn() {
        val p = TimelineRules.placement(3.99, 3.99, 14) as TimelinePlacement.Visible
        assertEquals(3.99 + TimelineRules.MIN_BAR_DAYS, p.end, 1e-9)
    }

    @Test
    fun tappingAnArrow_movesToShowThatDay() {
        assertEquals(LocalDate.of(2026, 10, 26), TimelineRules.startShowing(LocalDate.of(2026, 10, 30), TimelineZoom.TWO_WEEKS))
        assertEquals(LocalDate.of(2026, 10, 19), TimelineRules.startShowing(LocalDate.of(2026, 10, 30), TimelineZoom.MONTH))
    }

    @Test
    fun switchingZoom_keepsTodayInView() {
        // Month view of 9 Nov – 13 Dec with today 15 Nov, back to 2 weeks: this week again.
        val start = TimelineRules.startAfterZoom(LocalDate.of(2026, 11, 9), TimelineZoom.MONTH, TimelineZoom.TWO_WEEKS, sunday15)
        assertEquals(LocalDate.of(2026, 11, 9), start)
        assertEquals(LocalDate.of(2026, 11, 2), TimelineRules.startAfterZoom(start, TimelineZoom.TWO_WEEKS, TimelineZoom.MONTH, sunday15))
    }

    @Test
    fun switchingZoom_whileLookingElsewhere_keepsTheMiddle() {
        // Looking at January (today not visible): the middle day, 18 Jan, stays in view.
        val start = TimelineRules.startAfterZoom(LocalDate.of(2027, 1, 11), TimelineZoom.TWO_WEEKS, TimelineZoom.MONTH, sunday15)
        assertEquals(LocalDate.of(2027, 1, 11), start)
    }
}
