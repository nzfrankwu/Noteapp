package com.nzfrankwu.noteapp.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Checks the list of migrations itself (IDEA.md §5a). The emulator MigrationTest proves each
 * migration keeps data; this proves no step is missing, so every older version can be updated.
 */
class MigrationListTest {
    @Test
    fun noMigration_isNull() {
        // A migration declared below ALL_MIGRATIONS in Migrations.kt would be null here.
        ALL_MIGRATIONS.forEachIndexed { index, migration -> assertNotNull("Migration #$index is null", migration as Any?) }
    }

    @Test
    fun migrations_goOneVersionAtATime_fromVersion1_toTheCurrentVersion() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        val expected = (1 until NoteAppDatabase.VERSION).map { it to it + 1 }
        assertEquals(expected, steps)
    }
}
