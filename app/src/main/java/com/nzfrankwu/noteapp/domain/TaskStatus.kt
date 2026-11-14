package com.nzfrankwu.noteapp.domain

/**
 * A task's progress tag (IDEA.md §2.3), in workflow order.
 *
 * [key] is what gets saved in the database. A key must NEVER change or be removed once the app
 * is in use, or existing tasks would lose their status. To change what the user sees, change
 * [label] instead. (TaskStatusTest freezes the list of keys.)
 *
 * "Overdue" is deliberately not a status: it's worked out from the due date (IDEA.md §2.3).
 */
enum class TaskStatus(val key: String, val label: String) {
    NOT_STARTED("not_started", "Not started"),
    STARTED("started", "Started"),
    STUCK("stuck", "Stuck / need help"),
    DOUBLE_CHECKING("double_checking", "Double-checking"),
    SUBMITTED("submitted", "Submitted"),
    DONE("done", "Done");

    /** Submitted or Done: no longer counts as overdue, gets no reminders, and is hidden from Due soon. */
    val isFinished: Boolean get() = this == SUBMITTED || this == DONE

    companion object {
        /** Unknown keys fall back to [NOT_STARTED] instead of crashing the app. */
        fun fromKey(key: String): TaskStatus = entries.firstOrNull { it.key == key } ?: NOT_STARTED
    }
}
