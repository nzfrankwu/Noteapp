package com.nzfrankwu.noteapp.domain

/**
 * Where a task came from. Canvas tasks get their title, due date, description and link from
 * Canvas on every sync; your own (manual) tasks are entirely yours to edit.
 *
 * [key] is saved in the database and must never change (TaskStatusTest checks this too).
 */
enum class TaskSource(val key: String) {
    MANUAL("manual"),
    CANVAS("canvas");

    companion object {
        /** Unknown keys fall back to [MANUAL], so the task stays editable instead of crashing. */
        fun fromKey(key: String): TaskSource = entries.firstOrNull { it.key == key } ?: MANUAL
    }
}
