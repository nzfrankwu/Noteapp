package com.nzfrankwu.noteapp.domain

/**
 * The colours you can choose for a course (Phase 7, IDEA.md §2.2), saved in `courses.color` by
 * [key]. The keys are stored, so they must NEVER change (StoredKeysTest); they follow the automatic
 * palette's order (ui/theme/CourseColors.kt), so [slot] is the palette position.
 */
enum class CourseColorChoice(val key: String, val label: String) {
    INDIGO("indigo", "Indigo"),
    OLIVE("olive", "Olive"),
    SKY("sky", "Sky"),
    MAGENTA("magenta", "Magenta"),
    TEAL("teal", "Teal"),
    PURPLE("purple", "Purple"),
    GREEN("green", "Green");

    /** The colour's position in the course palette. */
    val slot: Int get() = ordinal

    companion object {
        /** null (automatic) for null or an unknown key. */
        fun fromKey(key: String?): CourseColorChoice? = entries.firstOrNull { it.key == key }
    }
}
