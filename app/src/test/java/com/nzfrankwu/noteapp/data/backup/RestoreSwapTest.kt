package com.nzfrankwu.noteapp.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreSwapTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var data: File
    private lateinit var swap: RestoreSwap
    private var clock = 1_000L

    private fun file(path: String) = File(data, path)
    private fun write(path: String, text: String) = file(path).apply { parentFile!!.mkdirs(); writeText(text) }

    @Before
    fun setUp() {
        data = tmp.newFolder("data")
        swap = RestoreSwap(data, thumbnailsDir = File(data, "cache/thumbnails"), now = { clock })
        // What's on the tablet now.
        write("databases/noteapp.db", "old db")
        write("databases/noteapp.db-wal", "old wal")
        write("files/notebooks/old-nb/p.ink", "old page")
        write("files/datastore/ink_settings.preferences_pb", "old pens")
        write("files/datastore/canvas_settings.preferences_pb", "canvas sign-in")
        write("cache/thumbnails/old-nb.png", "tile")
        // The checked backup, staged.
        write("restore-staging/manifest.json", "{}")
        write("restore-staging/db/noteapp.db", "new db")
        write("restore-staging/files/notebooks/new-nb/p.ink", "new page")
        write("restore-staging/files/datastore/ink_settings.preferences_pb", "new pens")
        write("restore-staging/files/datastore/view_settings.preferences_pb", "new view")
    }

    private fun assertRestored() {
        assertEquals("new db", file("databases/noteapp.db").readText())
        assertFalse(file("databases/noteapp.db-wal").exists()) // the old side file must not mix in
        assertEquals("new page", file("files/notebooks/new-nb/p.ink").readText())
        assertFalse(file("files/notebooks/old-nb").exists())
        assertEquals("new pens", file("files/datastore/ink_settings.preferences_pb").readText())
        assertEquals("new view", file("files/datastore/view_settings.preferences_pb").readText())
        // The Canvas sign-in is never touched by a restore.
        assertEquals("canvas sign-in", file("files/datastore/canvas_settings.preferences_pb").readText())
        assertFalse(swap.staging.exists())
        assertFalse(swap.isPending)
        assertFalse(file("cache/thumbnails").exists())
        // The old data is kept, not deleted.
        val before = File(swap.beforeRoot, "1000")
        assertEquals("old db", File(before, "databases/noteapp.db").readText())
        assertEquals("old wal", File(before, "databases/noteapp.db-wal").readText())
        assertEquals("old page", File(before, "files/notebooks/old-nb/p.ink").readText())
        assertEquals("old pens", File(before, "files/datastore/ink_settings.preferences_pb").readText())
    }

    @Test
    fun nothingPending_changesNothing() {
        assertEquals(RestoreSwap.Outcome.Nothing, swap.finishPending())
        assertEquals("old db", file("databases/noteapp.db").readText())
        assertTrue(swap.staging.exists()) // a checked set the user hasn't confirmed stays until cancelled
    }

    @Test
    fun aReadyRestore_isSwappedIn_andTheOldDataKept() {
        swap.markReady(backupCreatedAt = 42)
        assertTrue(swap.isPending)
        assertEquals(RestoreSwap.Outcome.Restored(42), swap.finishPending())
        assertRestored()
        val copies = swap.beforeRestoreCopies()
        assertEquals(1, copies.size)
        assertEquals(1000L, copies[0].restoredAt)
        assertTrue(copies[0].size > 0)
        swap.deleteCopy(copies[0])
        assertTrue(swap.beforeRestoreCopies().isEmpty())
    }

    @Test
    fun runningAgainAfterwards_doesNothing() {
        swap.markReady(42)
        swap.finishPending()
        assertEquals(RestoreSwap.Outcome.Nothing, swap.finishPending())
        assertRestored()
    }

    @Test
    fun aCrashHalfwayThroughMovingOut_isFinishedOnTheNextStart() {
        swap.markReady(42)
        // Simulate: the database was moved out, then the power went.
        val before = File(swap.beforeRoot, "1000/databases").apply { mkdirs() }
        file("databases/noteapp.db").renameTo(File(before, "noteapp.db"))
        assertEquals(RestoreSwap.Outcome.Restored(42), swap.finishPending())
        assertRestored()
    }

    @Test
    fun aCrashHalfwayThroughMovingIn_isFinishedOnTheNextStart() {
        swap.markReady(42)
        // Run phase 1 and part of phase 2 by hand: everything out, then only the database in.
        val before = File(swap.beforeRoot, "1000")
        for (path in listOf("databases/noteapp.db", "databases/noteapp.db-wal", "files/notebooks", "files/datastore/ink_settings.preferences_pb")) {
            File(before, path).parentFile!!.mkdirs()
            file(path).renameTo(File(before, path))
        }
        File(data, "restore-ready.json").writeText("""{"phase":2,"stamp":1000,"backupCreatedAt":42}""")
        File(swap.staging, "db/noteapp.db").renameTo(file("databases/noteapp.db"))
        assertEquals(RestoreSwap.Outcome.Restored(42), swap.finishPending())
        assertRestored()
    }

    @Test
    fun cancel_forgetsTheStagedSet() {
        swap.cancel()
        assertFalse(swap.staging.exists())
        assertEquals("old db", file("databases/noteapp.db").readText())
    }

    @Test
    fun anUnreadableMarker_changesNothing() {
        File(data, "restore-ready.json").writeText("garbage")
        val outcome = swap.finishPending()
        assertTrue(outcome is RestoreSwap.Outcome.Failed)
        assertEquals("old db", file("databases/noteapp.db").readText())
        assertEquals("old page", file("files/notebooks/old-nb/p.ink").readText())
        assertFalse(swap.isPending)
    }

    @Test
    fun aBackupWithoutViewSettings_resetsThemToDefaults_butKeepsTheOldCopy() {
        File(swap.staging, "files/datastore/view_settings.preferences_pb").delete()
        write("files/datastore/view_settings.preferences_pb", "old view")
        swap.markReady(42)
        swap.finishPending()
        assertFalse(file("files/datastore/view_settings.preferences_pb").exists())
        assertEquals("old view", File(swap.beforeRoot, "1000/files/datastore/view_settings.preferences_pb").readText())
    }
}
