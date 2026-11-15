package com.nzfrankwu.noteapp.domain

/**
 * The paper pattern drawn behind a page's handwriting (IDEA.md §2.5).
 *
 * [key] is what gets saved in the database. A key must NEVER change or be removed once the app
 * is in use, or notebooks would lose their pattern. To change what the user sees, change [label].
 * (StoredKeysTest freezes the list of keys.)
 */
enum class PageTemplate(val key: String, val label: String) {
    LINED("lined", "Lined"),
    BLANK("blank", "Blank"),
    GRID("grid", "Grid"),
    DOTS("dots", "Dot grid");

    companion object {
        /** New notebooks start lined (IDEA.md §2.5). */
        val DEFAULT = LINED

        /** Unknown keys (e.g. saved by a newer app version) fall back to [LINED] instead of crashing. */
        fun fromKey(key: String): PageTemplate = entries.firstOrNull { it.key == key } ?: LINED
    }
}
