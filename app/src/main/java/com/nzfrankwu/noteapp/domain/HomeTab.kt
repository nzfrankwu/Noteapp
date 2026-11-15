package com.nzfrankwu.noteapp.domain

/**
 * The views at the top of home (IDEA.md §2.2): Overview · Calendar · Timeline.
 * The last one used is remembered by its fixed [key] (never the Kotlin name), so renaming a tab
 * in code can't lose the setting.
 */
enum class HomeTab(val key: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    CALENDAR("calendar", "Calendar"),
    TIMELINE("timeline", "Timeline"),
    ;

    companion object {
        /** The tab saved under [key]; Overview when nothing (or something unknown) is saved. */
        fun fromKey(key: String?): HomeTab = entries.firstOrNull { it.key == key } ?: OVERVIEW
    }
}
