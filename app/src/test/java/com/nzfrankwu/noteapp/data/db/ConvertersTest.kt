package com.nzfrankwu.noteapp.data.db

import com.nzfrankwu.noteapp.domain.TaskSource
import com.nzfrankwu.noteapp.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun time_isSavedAndReadBackExactly() {
        val time = Instant.parse("2026-11-14T10:15:30.123Z")
        assertEquals(time, converters.millisToInstant(converters.instantToMillis(time)))
    }

    @Test
    fun missingTime_staysMissing() {
        assertNull(converters.instantToMillis(null))
        assertNull(converters.millisToInstant(null))
    }

    @Test
    fun everyStatus_isSavedAndReadBack() {
        for (status in TaskStatus.entries) {
            assertEquals(status, converters.keyToStatus(converters.statusToKey(status)))
        }
    }

    @Test
    fun status_isSavedByItsFixedKey() {
        assertEquals("stuck", converters.statusToKey(TaskStatus.STUCK))
    }

    @Test
    fun unknownStatus_fallsBackToNotStarted() {
        assertEquals(TaskStatus.NOT_STARTED, converters.keyToStatus("some_future_status"))
    }

    @Test
    fun everySource_isSavedAndReadBack() {
        for (source in TaskSource.entries) {
            assertEquals(source, converters.keyToSource(converters.sourceToKey(source)))
        }
    }

    @Test
    fun unknownSource_fallsBackToManual() {
        assertEquals(TaskSource.MANUAL, converters.keyToSource("some_future_source"))
    }
}
