package com.nzfrankwu.noteapp.canvas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncPolicyTest {
    private val now = Instant.parse("2026-11-15T09:00:00Z")

    @Test
    fun neverSynced_syncsOnOpen() {
        assertTrue(SyncPolicy.shouldSyncOnAppOpen(null, now))
    }

    @Test
    fun syncedRecently_doesNotSyncAgain() {
        assertFalse(SyncPolicy.shouldSyncOnAppOpen(now.minusSeconds(14 * 60), now))
    }

    @Test
    fun fifteenMinutesOrMore_syncsAgain() {
        assertTrue(SyncPolicy.shouldSyncOnAppOpen(now.minusSeconds(15 * 60), now))
        assertTrue(SyncPolicy.shouldSyncOnAppOpen(now.minusSeconds(6 * 3600), now))
    }
}
