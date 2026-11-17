package com.nzfrankwu.noteapp.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * When a task's reminders come, chosen in Settings → Reminders (Phase 7, 7.5; IDEA.md §2.3).
 * [key] is stable: it's saved in the settings and used in alarm IDs (StoredKeysTest freezes it).
 * Listed earliest first.
 */
enum class ReminderOffset(val key: String, val label: String) {
    WEEK_BEFORE("1w", "1 week before"),
    THREE_DAYS_BEFORE("3d", "3 days before"),
    DAY_BEFORE("1d", "1 day before"),
    TWO_HOURS_BEFORE("2h", "2 hours before"),
    HOUR_BEFORE("1h", "1 hour before"),
    AT_TIME("0", "At the time");

    /**
     * When this reminder fires for [target]. Whole days keep the same clock time (so across a
     * daylight-saving change "1 day before" is 23 or 25 hours earlier, as people expect); hours
     * are exact.
     */
    fun before(target: Instant, zone: ZoneId): Instant = when (this) {
        WEEK_BEFORE -> target.atZone(zone).minusDays(7).toInstant()
        THREE_DAYS_BEFORE -> target.atZone(zone).minusDays(3).toInstant()
        DAY_BEFORE -> target.atZone(zone).minusDays(1).toInstant()
        TWO_HOURS_BEFORE -> target.minus(Duration.ofHours(2))
        HOUR_BEFORE -> target.minus(Duration.ofHours(1))
        AT_TIME -> target
    }

    companion object {
        /** What you get until you choose (the reminders Noteapp always had). */
        val DEFAULT: Set<ReminderOffset> = setOf(DAY_BEFORE, TWO_HOURS_BEFORE)

        fun fromKey(key: String): ReminderOffset? = entries.firstOrNull { it.key == key }

        /** Saved as "1d,2h"; null (never chosen) = [DEFAULT], "" = no reminders; unknown keys are skipped. */
        fun decode(text: String?): Set<ReminderOffset> =
            if (text == null) DEFAULT else text.split(',').mapNotNull { fromKey(it.trim()) }.toSet()

        fun encode(offsets: Set<ReminderOffset>): String = entries.filter { it in offsets }.joinToString(",") { it.key }

        /**
         * Settings' sentence: "You get a reminder <this> each task's target date …", e.g.
         * "1 day and 2 hours before", "2 hours before and at", "at". Empty for none.
         */
        fun describe(offsets: Set<ReminderOffset>): String {
            val before = entries.filter { it in offsets && it != AT_TIME }.map {
                when (it) {
                    WEEK_BEFORE -> "1 week"
                    THREE_DAYS_BEFORE -> "3 days"
                    DAY_BEFORE -> "1 day"
                    TWO_HOURS_BEFORE -> "2 hours"
                    else -> "1 hour"
                }
            }
            val text = when (before.size) {
                0 -> ""
                1 -> before[0] + " before"
                else -> before.dropLast(1).joinToString(", ") + " and " + before.last() + " before"
            }
            return when {
                AT_TIME !in offsets -> text
                text.isEmpty() -> "at"
                else -> "$text and at"
            }
        }
    }
}

/** One planned reminder. [id] is unique per task and offset, so rescheduling replaces it. */
data class PlannedReminder(val taskId: String, val offset: ReminderOffset, val at: Instant) {
    val id: String get() = "$taskId/${offset.key}"
}

/**
 * When a task's reminders should fire:
 * - Based on the effective date: "My target date" if set, otherwise the due date.
 * - One reminder per chosen [ReminderOffset] (Settings → Reminders; [ReminderOffset.DEFAULT] is
 *   1 day and 2 hours before). See [ReminderOffset.before] for how days and hours count.
 * - None for Done tasks or tasks with no date at all, and none in the past.
 */
object ReminderPlanner {
    fun plan(
        taskId: String,
        task: TaskLike,
        now: Instant,
        zone: ZoneId,
        offsets: Set<ReminderOffset> = ReminderOffset.DEFAULT,
    ): List<PlannedReminder> {
        if (task.status.isFinished) return emptyList()
        val target = TaskRules.effectiveDate(task) ?: return emptyList()
        return ReminderOffset.entries.filter { it in offsets }
            .map { PlannedReminder(taskId, it, it.before(target, zone)) }
            .filter { it.at.isAfter(now) }
    }

    /** The notification's text, e.g. "Due tomorrow at 11:59 pm · ELECTENG 204". */
    fun message(task: TaskLike, offset: ReminderOffset, courseCode: String?, zone: ZoneId): String {
        val usesTarget = task.targetAt != null
        val whenText = when (offset) {
            ReminderOffset.WEEK_BEFORE -> "in a week"
            ReminderOffset.THREE_DAYS_BEFORE -> "in 3 days"
            ReminderOffset.DAY_BEFORE -> "tomorrow"
            ReminderOffset.TWO_HOURS_BEFORE -> "in 2 hours"
            ReminderOffset.HOUR_BEFORE -> "in 1 hour"
            ReminderOffset.AT_TIME -> "now"
        }
        val time = TaskRules.effectiveDate(task)?.atZone(zone)?.format(timeFormat)?.lowercase(Locale.ENGLISH)
        val lead = if (usesTarget) "Your target is $whenText" else "Due $whenText"
        val main = if (time != null) "$lead at $time" else lead
        return if (courseCode != null) "$main · $courseCode" else main
    }

    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
}
