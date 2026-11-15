package com.nzfrankwu.noteapp.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How dates and times are written across the app, e.g. "Fri 20 Nov" and "11:59 pm". */
object DateText {
    private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val dateWithYearFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val time24Format = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /** "Fri 20 Nov", plus the year when it isn't [thisYear] ("Mon 4 Jan 2027"). */
    fun date(date: LocalDate, thisYear: Int? = null): String =
        date.format(if (thisYear == null || date.year == thisYear) dateFormat else dateWithYearFormat)

    /** "11:59 pm", or "23:59" when the tablet is set to 24-hour time. */
    fun time(time: LocalTime, use24Hour: Boolean = false): String =
        if (use24Hour) time.format(time24Format) else time.format(timeFormat).replace("AM", "am").replace("PM", "pm")

    /** "November 2026". */
    fun month(month: YearMonth): String = month.format(monthFormat)

    /** "Fri 20 Nov, 11:59 pm" in the given time zone (year added when it isn't this year). */
    fun dateTime(instant: Instant, zone: ZoneId, now: Instant? = null): String {
        val local = instant.atZone(zone)
        val thisYear = now?.atZone(zone)?.year
        return "${date(local.toLocalDate(), thisYear)}, ${time(local.toLocalTime())}"
    }
}
