package com.nzfrankwu.noteapp.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How dates and times are written across the app, e.g. "Fri 20 Nov" and "11:59 pm". */
object DateText {
    private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val dateWithYearFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    /** "Fri 20 Nov", plus the year when it isn't [thisYear] ("Mon 4 Jan 2027"). */
    fun date(date: LocalDate, thisYear: Int? = null): String =
        date.format(if (thisYear == null || date.year == thisYear) dateFormat else dateWithYearFormat)

    /** "11:59 pm". */
    fun time(time: LocalTime): String = time.format(timeFormat).replace("AM", "am").replace("PM", "pm")

    /** "Fri 20 Nov, 11:59 pm" in the given time zone (year added when it isn't this year). */
    fun dateTime(instant: Instant, zone: ZoneId, now: Instant? = null): String {
        val local = instant.atZone(zone)
        val thisYear = now?.atZone(zone)?.year
        return "${date(local.toLocalDate(), thisYear)}, ${time(local.toLocalTime())}"
    }
}
