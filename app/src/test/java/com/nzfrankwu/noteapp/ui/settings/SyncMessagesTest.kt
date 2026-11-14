package com.nzfrankwu.noteapp.ui.settings

import com.nzfrankwu.noteapp.canvas.SyncOutcome
import com.nzfrankwu.noteapp.canvas.SyncSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMessagesTest {
    @Test
    fun syncedSummary_isPlainLanguage() {
        val text = SyncMessages.describe(SyncOutcome.Synced(SyncSummary(3, 7, 1, 0, emptyList())))
        assertEquals("Synced with Canvas: 3 courses, 7 new, 1 updated.", text)
    }

    @Test
    fun oneCourse_isSingular_andFailedCoursesAreNamed() {
        val text = SyncMessages.describe(SyncOutcome.Synced(SyncSummary(1, 0, 0, 2, listOf("COMPSYS 201"))))
        assertEquals("Synced with Canvas: 1 course, 2 removed from Canvas. Couldn't read COMPSYS 201; will try again.", text)
    }

    @Test
    fun expiredSignIn_saysTasksAreKept() {
        assertTrue(SyncMessages.describe(SyncOutcome.SignInExpired).contains("Your tasks are kept"))
    }
}
