package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * These keys are saved in the database. Changing or removing one would make existing tasks lose
 * their status (IDEA.md §5a). If this test fails, undo the key change; to rename what the user
 * sees, change the label instead. Adding a NEW status is fine: add its key here too.
 */
class StoredKeysTest {
    @Test
    fun statusKeys_neverChange() {
        assertEquals(
            setOf("not_started", "started", "stuck", "double_checking", "done"),
            TaskStatus.entries.map { it.key }.toSet(),
        )
    }

    @Test
    fun mergedStatusKeys_stillRead() {
        // "submitted" was merged into Done in Phase 7 (database v8 rewrites it); an old export
        // or a row not yet migrated must still read as Done, never fall back to Not started.
        assertEquals(mapOf("submitted" to TaskStatus.DONE), TaskStatus.LEGACY_KEYS)
        assertEquals(TaskStatus.DONE, TaskStatus.fromKey("submitted"))
        assertEquals(TaskStatus.NOT_STARTED, TaskStatus.fromKey("handed_in"))
        for (key in TaskStatus.LEGACY_KEYS.keys) {
            assertEquals("A legacy key can't also be a current one", null, TaskStatus.entries.firstOrNull { it.key == key })
        }
    }

    @Test
    fun courseColorKeys_neverChange() {
        // Saved in courses.color, in the automatic palette's order (the slot is the position).
        assertEquals(
            listOf("indigo", "olive", "sky", "magenta", "teal", "purple", "green"),
            CourseColorChoice.entries.map { it.key },
        )
        assertEquals(null, CourseColorChoice.fromKey(null))
        assertEquals(null, CourseColorChoice.fromKey("orange"))
        assertEquals(4, CourseColorChoice.fromKey("teal")?.slot)
    }

    @Test
    fun sourceKeys_neverChange() {
        assertEquals(setOf("manual", "canvas"), TaskSource.entries.map { it.key }.toSet())
    }

    @Test
    fun homeTabKeys_neverChange() {
        // Saved as "the last tab you used"; changing one would just reset to Overview, but still.
        assertEquals(setOf("overview", "calendar", "timeline"), HomeTab.entries.map { it.key }.toSet())
    }

    @Test
    fun unknownOrMissingHomeTab_isOverview() {
        assertEquals(HomeTab.OVERVIEW, HomeTab.fromKey(null))
        assertEquals(HomeTab.OVERVIEW, HomeTab.fromKey("agenda"))
        assertEquals(HomeTab.TIMELINE, HomeTab.fromKey("timeline"))
    }

    @Test
    fun pageTemplateKeys_neverChange() {
        assertEquals(setOf("lined", "blank", "grid", "dots"), PageTemplate.entries.map { it.key }.toSet())
    }

    @Test
    fun unknownPageTemplate_isLined() {
        assertEquals(PageTemplate.LINED, PageTemplate.fromKey("hexagons"))
        assertEquals(PageTemplate.DOTS, PageTemplate.fromKey("dots"))
        assertEquals(PageTemplate.LINED, PageTemplate.DEFAULT)
    }

    @Test
    fun keys_areUnique() {
        assertEquals(TaskStatus.entries.size, TaskStatus.entries.map { it.key }.toSet().size)
        assertEquals(TaskSource.entries.size, TaskSource.entries.map { it.key }.toSet().size)
        assertEquals(PageTemplate.entries.size, PageTemplate.entries.map { it.key }.toSet().size)
    }
}
