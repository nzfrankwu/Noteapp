package com.nzfrankwu.noteapp.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The two reminders a task gets (IDEA.md §2.3; configurable later). [key] is stable, used in IDs. */
enum class ReminderOffset(val key: String) {
    DAY_BEFORE("1d"),
    TWO_HOURS_BEFORE("2h"),
}

/** One planned reminder. [id] is unique per task and offset, so rescheduling replaces it. */
data class PlannedReminder(val taskId: String, val offset: ReminderOffset, val at: Instant) {
    val id: String get() = "$taskId/${offset.key}"
}

/**
 * When a task's reminders should fire:
 * - Based on the effective date: "My target date" if set, otherwise the due date.
 * - "1 day before" keeps the same clock time on the previous day (so across a daylight-saving
 *   change it's 23 or 25 hours earlier, as people expect). "2 hours before" is exactly 2 hours.
 * - None for Done tasks or tasks with no date at all, and none in the past.
 */
object ReminderPlanner {
    fun plan(taskId: String, task: TaskLike, now: Instant, zone: ZoneId): List<PlannedReminder> {
        if (task.status.isFinished) return emptyList()
        val target = TaskRules.effectiveDate(task) ?: return emptyList()
        return listOf(
            PlannedReminder(taskId, ReminderOffset.DAY_BEFORE, target.atZone(zone).minusDays(1).toInstant()),
            PlannedReminder(taskId, ReminderOffset.TWO_HOURS_BEFORE, target.minus(Duration.ofHours(2))),
        ).filter { it.at.isAfter(now) }
    }

    /** The notification's text, e.g. "Due tomorrow at 11:59 pm · ELECTENG 204". */
    fun message(task: TaskLike, offset: ReminderOffset, courseCode: String?, zone: ZoneId): String {
        val usesTarget = task.targetAt != null
        val whenText = when (offset) {
            ReminderOffset.DAY_BEFORE -> "tomorrow"
            ReminderOffset.TWO_HOURS_BEFORE -> "in 2 hours"
        }
        val time = TaskRules.effectiveDate(task)?.atZone(zone)?.format(timeFormat)?.lowercase(Locale.ENGLISH)
        val lead = if (usesTarget) "Your target is $whenText" else "Due $whenText"
        val main = if (time != null) "$lead at $time" else lead
        return if (courseCode != null) "$main · $courseCode" else main
    }

    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
}
