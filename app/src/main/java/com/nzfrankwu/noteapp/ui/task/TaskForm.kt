package com.nzfrankwu.noteapp.ui.task

import com.nzfrankwu.noteapp.data.TaskDraft
import com.nzfrankwu.noteapp.data.db.Task
import com.nzfrankwu.noteapp.domain.TaskDates
import com.nzfrankwu.noteapp.domain.TaskSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What's typed into the New task / Edit task form. Dates and times are kept as the local date
 * and (optional) time you picked; [toDraft] turns them into the instants that get saved.
 *
 * A date without a time uses the default time (due: 11:59 pm, target: 9:00 am, see [TaskDates]).
 * When editing, a date and time you didn't change keep their exact saved instant (Canvas due
 * times often have seconds, e.g. 11:59:59 pm, which the form doesn't show).
 */
data class TaskForm(
    val title: String = "",
    val courseId: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val targetDate: LocalDate? = null,
    val targetTime: LocalTime? = null,
    val url: String = "",
    val notes: String = "",
    /** For Canvas tasks only target date and notes can be changed; the rest is shown read-only. */
    val isCanvas: Boolean = false,
    val originalDueAt: Instant? = null,
    val originalTargetAt: Instant? = null,
) {
    /** Picking or clearing a date. Clearing the date also clears its time. */
    fun withDueDate(date: LocalDate?) = copy(dueDate = date, dueTime = if (date == null) null else dueTime)
    fun withTargetDate(date: LocalDate?) = copy(targetDate = date, targetTime = if (date == null) null else targetTime)

    /** Setting a time is ignored until there's a date to go with it. */
    fun withDueTime(time: LocalTime?) = if (dueDate == null) this else copy(dueTime = time)
    fun withTargetTime(time: LocalTime?) = if (targetDate == null) this else copy(targetTime = time)

    fun dueAt(zone: ZoneId): Instant? =
        resolve(dueDate, dueTime, TaskDates.DefaultDueTime, originalDueAt, zone)

    fun targetAt(zone: ZoneId): Instant? =
        resolve(targetDate, targetTime, TaskDates.DefaultTargetTime, originalTargetAt, zone)

    /** Whether Save makes sense yet (the repository still checks everything). */
    val canSave: Boolean get() = isCanvas || (title.isNotBlank() && courseId != null)

    fun toDraft(zone: ZoneId) = TaskDraft(
        title = title,
        courseId = courseId,
        dueAt = dueAt(zone),
        targetAt = targetAt(zone),
        url = url,
        notes = notes,
    )

    companion object {
        /** A blank form for a new task, optionally prefilled (from a folder, a shared link or a calendar day). */
        fun new(courseId: String? = null, title: String? = null, url: String? = null, dueDate: LocalDate? = null) =
            TaskForm(title = title.orEmpty(), courseId = courseId, url = url.orEmpty()).withDueDate(dueDate)

        /** The form for editing [task], showing its dates in [zone]. */
        fun from(task: Task, zone: ZoneId): TaskForm {
            val due = task.dueAt?.atZone(zone)
            val target = task.targetAt?.atZone(zone)
            return TaskForm(
                title = task.title,
                courseId = task.courseId,
                dueDate = due?.toLocalDate(),
                dueTime = due?.toLocalTime()?.truncatedTo(ChronoUnit.MINUTES),
                targetDate = target?.toLocalDate(),
                targetTime = target?.toLocalTime()?.truncatedTo(ChronoUnit.MINUTES),
                url = task.url.orEmpty(),
                notes = task.notes.orEmpty(),
                isCanvas = task.source == TaskSource.CANVAS,
                originalDueAt = task.dueAt,
                originalTargetAt = task.targetAt,
            )
        }

        private fun resolve(date: LocalDate?, time: LocalTime?, default: LocalTime, original: Instant?, zone: ZoneId): Instant? {
            if (date == null) return null
            val picked = date.atTime(time ?: default)
            // Unchanged since loading (to the minute): keep the exact saved instant.
            if (original != null && original.atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES) == picked) {
                return original
            }
            return picked.atZone(zone).toInstant()
        }
    }
}
