package com.nzfrankwu.noteapp.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** How many days the timeline shows at once. */
enum class TimelineZoom(val label: String, val days: Int, val stepDays: Long) {
    /** This week and next (◀ ▶ move a week). */
    TWO_WEEKS("2 weeks", 14, 7),

    /** Last week plus the next four (◀ ▶ move four weeks). */
    MONTH("Month", 35, 28),
}

/** Where a task's bar is compared with the visible days. */
sealed interface TimelinePlacement {
    /** It ended before the first visible day: an arrow at the left edge. */
    data object Before : TimelinePlacement

    /** It starts after the last visible day: an arrow at the right edge. */
    data object After : TimelinePlacement

    /**
     * At least partly visible. [start] and [end] are in days from the first visible day, cut to
     * the visible days; [startsEarlier] / [endsLater] say whether the bar carries on off-screen.
     */
    data class Visible(val start: Double, val end: Double, val startsEarlier: Boolean, val endsLater: Boolean) : TimelinePlacement
}

/** The timeline's rules (PHASE-1B.md, B.6). Plain Kotlin, so every case is unit-tested. */
object TimelineRules {
    /** The shortest bar drawn, in days, so a task added just before it's due is still tappable. */
    const val MIN_BAR_DAYS = 0.35

    /** The first visible day for [zoom] around [today]: Monday this week (2 weeks) or last week (Month). */
    fun defaultStart(today: LocalDate, zoom: TimelineZoom): LocalDate {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return if (zoom == TimelineZoom.MONTH) monday.minusWeeks(1) else monday
    }

    /** The visible days, from [start]. */
    fun days(start: LocalDate, zoom: TimelineZoom): List<LocalDate> = (0 until zoom.days).map { start.plusDays(it.toLong()) }

    /** ◀ (-1) or ▶ (+1). */
    fun shift(start: LocalDate, zoom: TimelineZoom, direction: Int): LocalDate = start.plusDays(zoom.stepDays * direction)

    /**
     * When a task's bar starts: Canvas's "available from" if it has one, otherwise when the task
     * was added. Never after the due date (a bar can't run backwards).
     */
    fun barStart(availableAt: Instant?, createdAt: Instant, dueAt: Instant): Instant = minOf(availableAt ?: createdAt, dueAt)

    /** [instant] as days (with fractions) from the start of [firstDay], in local time. */
    fun position(instant: Instant, firstDay: LocalDate, zone: ZoneId): Double {
        val local = instant.atZone(zone)
        val wholeDays = ChronoUnit.DAYS.between(firstDay, local.toLocalDate())
        val dayStart = local.toLocalDate().atStartOfDay(zone)
        val dayLength = Duration.between(dayStart, local.toLocalDate().plusDays(1).atStartOfDay(zone)).toMillis().toDouble()
        return wholeDays + Duration.between(dayStart, local).toMillis() / dayLength
    }

    /** Where a bar from [start] to [end] (days from the first visible day) sits among [days] visible days. */
    fun placement(start: Double, end: Double, days: Int): TimelinePlacement {
        val barEnd = maxOf(end, start + MIN_BAR_DAYS)
        return when {
            barEnd <= 0.0 -> TimelinePlacement.Before
            start >= days -> TimelinePlacement.After
            else -> TimelinePlacement.Visible(
                start = start.coerceAtLeast(0.0),
                end = barEnd.coerceAtMost(days.toDouble()),
                startsEarlier = start < 0.0,
                endsLater = barEnd > days,
            )
        }
    }

    /**
     * Where to move the view when an off-screen arrow is tapped: so the day the bar is closest to
     * (its due day for an arrow on the left, its start day for one on the right) is in view.
     */
    fun startShowing(day: LocalDate, zoom: TimelineZoom): LocalDate = defaultStart(day, zoom)

    /**
     * The first visible day after switching from [old] to [new] zoom: keeps today in view if it
     * was, otherwise keeps the middle of what was showing.
     */
    fun startAfterZoom(start: LocalDate, old: TimelineZoom, new: TimelineZoom, today: LocalDate): LocalDate {
        val todayVisible = !today.isBefore(start) && today.isBefore(start.plusDays(old.days.toLong()))
        return startShowing(if (todayVisible) today else start.plusDays(old.days / 2L), new)
    }
}
