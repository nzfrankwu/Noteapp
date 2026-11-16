package com.nzfrankwu.noteapp.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards IDEA.md §5a ("never lose data on updates").
 *
 * Room re-exports app/schemas/.../<version>.json on every build. If someone changes a table but
 * forgets to bump the database version, that file silently changes, and tablets that already have
 * that version would crash or misread data. This test remembers each version's fingerprint
 * ("identity hash") so that mistake fails here instead.
 */
class SchemaHistoryTest {
    /**
     * Fingerprint of every database version. Once recorded, a version's entry must never change:
     * to change tables, bump NoteAppDatabase.VERSION, add a migration, and add a NEW line here.
     */
    private val recordedVersions = mapOf(
        1 to "b27669dff183eb5efc7d815cae29680e",
        2 to "cc7190b5bf8617347de1f80aff0e1987", // Phase 1b: tasks.available_at
        3 to "c46f5206b09771b08f5070500a2fe591", // Phase 1b: courses.archived_at
        4 to "a64c218ee32b0a66128cb5539c44d312", // Phase 2: subfolders, notebooks, pages
        5 to "89e756841cb2c3e2e68f5239b5327971", // Phase 3: page sizes, PDF pages
        6 to "6372421640f9306234ce83b64b287d14", // Phase 4: task-notebook links, bookmarks
    )

    // Unit tests run with the app module folder as the working directory.
    private val schemaDir = File("schemas/${NoteAppDatabase::class.java.name}")

    @Test
    fun everyVersion_hasAnExportedSchema() {
        for (version in 1..NoteAppDatabase.VERSION) {
            assertTrue("Missing schema file for version $version in $schemaDir", schemaFile(version).exists())
        }
    }

    @Test
    fun currentVersion_isRecorded() {
        assertTrue(
            "Database version ${NoteAppDatabase.VERSION} isn't recorded in SchemaHistoryTest. " +
                "Add its identity hash (from ${schemaFile(NoteAppDatabase.VERSION)}) to recordedVersions.",
            NoteAppDatabase.VERSION in recordedVersions,
        )
    }

    @Test
    fun recordedVersions_neverChange() {
        for ((version, recordedHash) in recordedVersions) {
            assertEquals(
                "The tables for database version $version changed without a version bump! " +
                    "Undo the table change, or bump NoteAppDatabase.VERSION and add a migration " +
                    "(see Migrations.kt).",
                recordedHash,
                identityHashOf(version),
            )
        }
    }

    private fun schemaFile(version: Int) = File(schemaDir, "$version.json")

    private fun identityHashOf(version: Int): String {
        val json = schemaFile(version).readText()
        val match = Regex("\"identityHash\"\\s*:\\s*\"([0-9a-f]+)\"").find(json)
        return checkNotNull(match) { "No identityHash in ${schemaFile(version)}" }.groupValues[1]
    }
}
