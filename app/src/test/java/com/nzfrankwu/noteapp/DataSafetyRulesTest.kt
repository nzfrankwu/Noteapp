package com.nzfrankwu.noteapp

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the app's source code for things that would put the user's notes at risk (IDEA.md §5a).
 */
class DataSafetyRulesTest {
    // Unit tests run with the app module folder as the working directory.
    private val mainSources = File("src/main")

    @Test
    fun noDestructiveMigrationFallback() {
        assertTrue("Can't find $mainSources (wrong working directory?)", mainSources.isDirectory)

        // Room's "fallbackToDestructive..." options delete the whole database whenever a migration
        // is missing or the app is downgraded. That would wipe every note.
        val offenders = mainSources.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .filter { "fallbackToDestructive" in it.readText() }
            .map { it.path }
            .toList()

        assertTrue(
            "Destructive database fallback found in $offenders. It would delete the user's notes; " +
                "write a migration instead (see Migrations.kt).",
            offenders.isEmpty(),
        )
    }
}
