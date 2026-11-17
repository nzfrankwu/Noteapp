package com.nzfrankwu.noteapp.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Where a task sits in the calendar: on its real due date, or, if it has only a "My target date",
 * on that date marked [isTarget].
 */
data class CalendarEntry<T : TaskLike>(
    val task: T,
    val date: LocalDate,
    val time: LocalTime,
    val isTarget: Boolean,
)

/** The month calendar's rules (PHASE-1B.md, B.5). Plain Kotlin, so every case is unit-tested. */
object CalendarRules {
    /** Most task chips in one day before the rest become "+N more". */
    const val MAX_CHIPS_PER_DAY = 3

    /**
     * The Monday-to-Sunday weeks that show [month] (4–6 weeks; the first and last week are filled
     * with days from the months around it). Same weeks as "This week" in Due soon.
     */
    fun weeks(month: YearMonth): List<List<LocalDate>> {
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val last = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .chunked(7)
            .toList()
    }

    /** Where [task] sits (local time in [zone]), or null if it has neither a due nor a target date. */
    fun <T : TaskLike> entryFor(task: T, zone: ZoneId): CalendarEntry<T>? {
        val (at, isTarget) = when {
            task.dueAt != null -> task.dueAt!! to false
            task.targetAt != null -> task.targetAt!! to true
            else -> return null
        }
        val local = at.atZone(zone)
        return CalendarEntry(task, local.toLocalDate(), local.toLocalTime(), isTarget)
    }

    /**
     * The tasks on each day from [from] to [to] (both included), each day ordered by time, then
     * title. Done tasks are left out unless [includeFinished].
     */
    fun <T : TaskLike> byDay(
        tasks: List<T>,
        zone: ZoneId,
        from: LocalDate,
        to: LocalDate,
        includeFinished: Boolean,
    ): Map<LocalDate, List<CalendarEntry<T>>> = tasks
        .asSequence()
        .filter { includeFinished || !it.status.isFinished }
        .mapNotNull { entryFor(it, zone) }
        .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        .sortedWith(compareBy<CalendarEntry<T>> { it.time }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.task.title })
        .groupBy { it.date }

    /**
     * How many of a day's [total] chips to show when [fit] chips fit in the cell and "+N more"
     * takes the room of one more chip: all of them if they fit (at most [MAX_CHIPS_PER_DAY]),
     * otherwise as many as fit beside the "+N more" line.
     */
    fun chipsShown(total: Int, fit: Int, max: Int = MAX_CHIPS_PER_DAY): Int =
        if (total <= minOf(fit, max)) total else (minOf(fit - 1, max)).coerceIn(0, total)
}
