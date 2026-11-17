package com.nzfrankwu.noteapp.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** What the task rules need to know about a task. The database's Task implements this. */
interface TaskLike {
    val title: String
    val dueAt: Instant?
    val targetAt: Instant?
    val status: TaskStatus
}

/** Rules about a single task's dates (IDEA.md §2.3). */
object TaskRules {
    /** The date used for sorting and reminders: "My target date" if set, otherwise the due date. */
    fun effectiveDate(task: TaskLike): Instant? = task.targetAt ?: task.dueAt

    /** Overdue = the real due date has passed and it isn't Done. Target dates never make a task overdue. */
    fun isOverdue(task: TaskLike, now: Instant): Boolean {
        val due = task.dueAt ?: return false
        return !task.status.isFinished && due < now
    }

    /** Your own target date has passed, but the real due date hasn't (a gentler warning than Overdue). */
    fun isPastTarget(task: TaskLike, now: Instant): Boolean {
        val target = task.targetAt ?: return false
        return !task.status.isFinished && !isOverdue(task, now) && target < now
    }
}

/** Turning a picked date (and maybe a time) into the instant that gets saved. */
object TaskDates {
    /** A due date picked without a time means the end of that day (11:59 pm). */
    val DefaultDueTime: LocalTime = LocalTime.of(23, 59)

    /** A target date picked without a time means 9:00 am (used for reminders). */
    val DefaultTargetTime: LocalTime = LocalTime.of(9, 0)

    fun dueAt(date: LocalDate, time: LocalTime?, zone: ZoneId): Instant =
        date.atTime(time ?: DefaultDueTime).atZone(zone).toInstant()

    fun targetAt(date: LocalDate, time: LocalTime?, zone: ZoneId): Instant =
        date.atTime(time ?: DefaultTargetTime).atZone(zone).toInstant()
}

/** The sections of a task list, in display order. */
enum class DueGroup(val title: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    THIS_WEEK("This week"),
    LATER("Later"),
    NO_DUE_DATE("No due date"),
    FINISHED("Finished"),
}

data class TaskSection<T>(val group: DueGroup, val tasks: List<T>)

/**
 * Splits tasks into the Due soon sections:
 * Overdue → Today → Tomorrow → This week → Later → No due date (→ Finished, if asked for).
 *
 * - A task goes in Overdue only if its real due date has passed (see [TaskRules.isOverdue]).
 * - Otherwise it's placed by its effective date (target date if set, else due date), in local time.
 *   A target date that has already passed (but not the due date) counts as Today.
 * - "This week" runs from the day after tomorrow to Sunday (weeks start on Monday).
 * - Done tasks are left out unless [includeFinished]; then they go in Finished.
 * - Within a section: Overdue by due date; others by effective date, then title. No due date by title.
 */
object TaskGrouping {
    fun <T : TaskLike> sections(
        tasks: List<T>,
        now: Instant,
        zone: ZoneId,
        includeFinished: Boolean = false,
    ): List<TaskSection<T>> {
        val today = now.atZone(zone).toLocalDate()
        val tomorrow = today.plusDays(1)
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        fun groupOf(task: T): DueGroup? {
            if (task.status.isFinished) return if (includeFinished) DueGroup.FINISHED else null
            if (TaskRules.isOverdue(task, now)) return DueGroup.OVERDUE
            val effective = TaskRules.effectiveDate(task) ?: return DueGroup.NO_DUE_DATE
            val day = effective.atZone(zone).toLocalDate()
            return when {
                !day.isAfter(today) -> DueGroup.TODAY
                day == tomorrow -> DueGroup.TOMORROW
                !day.isAfter(endOfWeek) -> DueGroup.THIS_WEEK
                else -> DueGroup.LATER
            }
        }

        val byTitle = compareBy<T, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        val byEffectiveDate = compareBy<T, Instant?>(nullsLast()) { TaskRules.effectiveDate(it) }.then(byTitle)
        val byDueDate = compareBy<T, Instant?>(nullsLast()) { it.dueAt }.then(byTitle)

        return tasks
            .mapNotNull { task -> groupOf(task)?.let { it to task } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (group, items) ->
                val order = when (group) {
                    DueGroup.OVERDUE -> byDueDate
                    DueGroup.NO_DUE_DATE -> byTitle
                    else -> byEffectiveDate
                }
                TaskSection(group, items.sortedWith(order))
            }
    }
}
