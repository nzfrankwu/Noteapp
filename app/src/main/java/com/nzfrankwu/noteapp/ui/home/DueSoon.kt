package com.nzfrankwu.noteapp.ui.home

import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.DueGroup
import com.nzfrankwu.noteapp.domain.TaskGrouping
import com.nzfrankwu.noteapp.domain.TaskLike
import com.nzfrankwu.noteapp.domain.TaskRules
import com.nzfrankwu.noteapp.domain.TaskSection
import java.time.Instant
import java.time.ZoneId

/** A task with its course code, usable by the task rules. */
class DueItem(val item: TaskWithCourse) : TaskLike by item.task {
    val id: String get() = item.task.id
}

/**
 * What the home screen's Due soon panel shows (IDEA.md §2.2, §2.3):
 * - [dated]: Overdue → Today → Tomorrow → This week → Later, across all courses.
 * - [noDueDate]: the collapsible "No due date" section (always counted, listed only when expanded).
 * - [finished]: Done tasks, listed only when "Show finished" is on.
 */
data class DueSoonState(
    val loaded: Boolean = false,
    val dated: List<TaskSection<DueItem>> = emptyList(),
    val noDueDate: List<DueItem> = emptyList(),
    val finished: List<DueItem> = emptyList(),
    val noDueDateExpanded: Boolean = false,
    val showFinished: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** True when there are no tasks at all. */
    val isEmpty: Boolean get() = dated.isEmpty() && noDueDate.isEmpty() && finished.isEmpty()

    companion object {
        fun build(
            tasks: List<TaskWithCourse>,
            now: Instant,
            zone: ZoneId,
            noDueDateExpanded: Boolean,
            showFinished: Boolean,
        ): DueSoonState {
            val sections = TaskGrouping.sections(tasks.map(::DueItem), now, zone, includeFinished = true)
            return DueSoonState(
                loaded = true,
                dated = sections.filter { it.group != DueGroup.NO_DUE_DATE && it.group != DueGroup.FINISHED },
                noDueDate = sections.firstOrNull { it.group == DueGroup.NO_DUE_DATE }?.tasks.orEmpty(),
                // Most recent first, so last week's work is at the top; undated ones at the end.
                finished = sections.firstOrNull { it.group == DueGroup.FINISHED }?.tasks.orEmpty()
                    .sortedWith(compareBy(nullsLast(reverseOrder())) { TaskRules.effectiveDate(it) }),
                noDueDateExpanded = noDueDateExpanded,
                showFinished = showFinished,
                now = now,
                zone = zone,
            )
        }
    }
}
