package com.nzfrankwu.noteapp.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How urgent a task's "days left" is; the UI picks the colour from this. */
enum class DaysLeftUrgency {
    /** 3 or more days left. */
    NORMAL,

    /** Due today, tomorrow or in 2 days (amber). */
    SOON,

    /** The real due date has passed (red). */
    OVERDUE,
}

/**
 * How long is left until a task's real due date, counted in local calendar days.
 *
 * [days] is the number of calendar days from today to the due day: 0 = today, 1 = tomorrow,
 * negative = that many days ago.
 */
data class DaysLeft(val days: Long, val overdue: Boolean) {
    val urgency: DaysLeftUrgency
        get() = when {
            overdue -> DaysLeftUrgency.OVERDUE
            days <= SOON_DAYS -> DaysLeftUrgency.SOON
            else -> DaysLeftUrgency.NORMAL
        }

    /** "Due today", "Due tomorrow", "3 days left", "1 day overdue", "5 days overdue", or "Was due today". */
    val text: String
        get() = when {
            overdue && days >= 0 -> "Was due today"
            overdue -> plural(-days) + " overdue"
            days == 0L -> "Due today"
            days == 1L -> "Due tomorrow"
            else -> plural(days) + " left"
        }

    private fun plural(n: Long) = if (n == 1L) "1 day" else "$n days"

    companion object {
        /** Up to this many days left counts as "soon" (amber). */
        const val SOON_DAYS = 2L

        /**
         * The days left for [task], or null when there's nothing to show: no due date, or the task is
         * Submitted/Done. Only the real due date counts ("My target date" never does), and days are
         * local calendar days, so daylight-saving changes don't shift the count.
         */
        fun of(task: TaskLike, now: Instant, zone: ZoneId): DaysLeft? {
            val due = task.dueAt ?: return null
            if (task.status.isFinished) return null
            val today = now.atZone(zone).toLocalDate()
            val dueDay = due.atZone(zone).toLocalDate()
            return DaysLeft(ChronoUnit.DAYS.between(today, dueDay), TaskRules.isOverdue(task, now))
        }
    }
}
