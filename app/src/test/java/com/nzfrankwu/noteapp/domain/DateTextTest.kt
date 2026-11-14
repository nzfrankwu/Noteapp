package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DateTextTest {
    private val nz = ZoneId.of("Pacific/Auckland")

    @Test
    fun dateAndTime_areWrittenTheNzWay() {
        assertEquals("Fri 20 Nov", DateText.date(LocalDate.of(2026, 11, 20)))
        assertEquals("11:59 pm", DateText.time(LocalTime.of(23, 59)))
        assertEquals("9:00 am", DateText.time(LocalTime.of(9, 0)))
    }

    @Test
    fun yearIsAddedOnlyWhenItIsNotThisYear() {
        assertEquals("Fri 20 Nov", DateText.date(LocalDate.of(2026, 11, 20), thisYear = 2026))
        assertEquals("Mon 4 Jan 2027", DateText.date(LocalDate.of(2027, 1, 4), thisYear = 2026))
    }

    @Test
    fun dateTime_usesTheLocalZone() {
        val instant = Instant.parse("2026-11-20T10:59:00Z") // 11:59 pm NZDT
        assertEquals("Fri 20 Nov, 11:59 pm", DateText.dateTime(instant, nz, now = instant))
    }
}
