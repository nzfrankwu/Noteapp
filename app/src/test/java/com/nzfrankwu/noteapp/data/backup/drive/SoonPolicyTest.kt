package com.nzfrankwu.noteapp.data.backup.drive

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A change while a backup uploads must never cancel it (on a slow connection it might never finish). */
class SoonPolicyTest {
    @Test
    fun nothingThere_orOnlyWaiting_isReplaced() {
        assertEquals(ExistingWorkPolicy.REPLACE, BackupWorker.soonPolicy(emptyList()))
        assertEquals(ExistingWorkPolicy.REPLACE, BackupWorker.soonPolicy(listOf(State.ENQUEUED)))
        assertEquals(ExistingWorkPolicy.REPLACE, BackupWorker.soonPolicy(listOf(State.SUCCEEDED)))
    }

    @Test
    fun aRunningBackup_getsOneMoreAfterIt_neverReplaced() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, BackupWorker.soonPolicy(listOf(State.RUNNING)))
    }

    @Test
    fun aRunningBackup_withOneAlreadyWaiting_needsNothingMore() {
        assertNull(BackupWorker.soonPolicy(listOf(State.RUNNING, State.BLOCKED)))
        assertNull(BackupWorker.soonPolicy(listOf(State.RUNNING, State.ENQUEUED)))
    }
}
