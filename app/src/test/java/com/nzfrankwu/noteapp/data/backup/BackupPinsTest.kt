package com.nzfrankwu.noteapp.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 7, 7.2: which notebooks a Drive upload still reads. */
class BackupPinsTest {
    @Test
    fun notebookOf_findsTheNotebookOfABackupPath() {
        assertEquals("abc", BackupPins.notebookOf("files/notebooks/abc/pdf-1.pdf"))
        assertEquals("abc", BackupPins.notebookOf("files/notebooks/abc/images/x.png"))
        assertNull(BackupPins.notebookOf("files/datastore/ink_settings.preferences_pb"))
        assertNull(BackupPins.notebookOf("db/noteapp.db"))
        assertNull(BackupPins.notebookOf("files/notebooks/loose-file"))
    }

    @Test
    fun twoRuns_pinningTheSameNotebook_keepItPinnedUntilBothEnd() {
        val first = BackupPins.pin(listOf("files/notebooks/pin-a/pdf-1.pdf", "files/notebooks/pin-a/images/x.png"))
        val second = BackupPins.pin(listOf("files/notebooks/pin-a/pdf-1.pdf", "files/notebooks/pin-b/pdf-2.pdf"))
        assertEquals(setOf("pin-a"), first)
        assertTrue(BackupPins.isPinned("pin-a"))
        BackupPins.unpin(first)
        assertTrue("the second run still reads it", BackupPins.isPinned("pin-a"))
        BackupPins.unpin(second)
        assertFalse(BackupPins.isPinned("pin-a"))
        assertFalse(BackupPins.isPinned("pin-b"))
    }
}
