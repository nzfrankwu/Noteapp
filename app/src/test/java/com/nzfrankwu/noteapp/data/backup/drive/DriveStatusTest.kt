package com.nzfrankwu.noteapp.data.backup.drive

import com.nzfrankwu.noteapp.data.backup.BackupCounts
import com.nzfrankwu.noteapp.data.backup.BackupEntry
import com.nzfrankwu.noteapp.data.backup.BackupManifest
import com.nzfrankwu.noteapp.data.backup.Md5
import com.nzfrankwu.noteapp.data.backup.Snapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration

/** PHASE-6.md 6.10: what the background job records, and the status words. */
class DriveStatusTest {
    @get:Rule val temp = TemporaryFolder()

    private val minute = Duration.ofMinutes(1).toMillis()
    private val day = Duration.ofDays(1).toMillis()
    private val now = 1_800_000_000_000L

    private class MemoryStore(var status: DriveStatus) : DriveStatusStore {
        override suspend fun current() = status
        override suspend fun recordSuccess(at: Long, storedBytes: Long) {
            status = status.copy(lastSuccessAt = at, storedBytes = storedBytes, lastError = null, needsSignIn = false)
        }
        override suspend fun recordFailure(message: String, needsSignIn: Boolean) {
            status = status.copy(lastError = message, needsSignIn = needsSignIn)
        }
        override suspend fun recordPdfSync(at: Long) {
            status = status.copy(pdfSyncedAt = at)
        }
    }

    private val drive = FakeDrive()
    private var restorePending = false

    private val pdfSource = object : PdfCopies.Source {
        override suspend fun wanted() = listOf(PdfCopies.Wanted("n1", "Lecture 1", listOf("Electromagnetics"), "v1"))
        override suspend fun allIds() = setOf("n1")
        override suspend fun export(notebookId: String, out: File) = out.writeText("%PDF-1.7")
    }

    private fun runner(store: DriveStatusStore): DriveBackupRunner {
        val backup = DriveBackup(drive, { staging ->
            val file = File(staging, "db/noteapp.db").apply { parentFile!!.mkdirs(); writeText("db") }
            Snapshot(
                BackupManifest(createdAt = now, appVersion = "test", databaseVersion = 7, counts = BackupCounts(notebooks = 1), files = listOf(BackupEntry("db/noteapp.db", 2, Md5.of(file)))),
                mapOf("db/noteapp.db" to file),
            )
        }, temp.newFolder())
        return DriveBackupRunner(store, backup, { restorePending }, PdfCopies(drive, pdfSource, temp.newFolder()), now = { now })
    }

    private val connected = DriveStatus(email = "test@example.com", connectedAt = now - day)

    @Test
    fun notConnected_doesNothing() = runBlocking {
        assertEquals(DriveBackupRunner.Outcome.NotConnected, runner(MemoryStore(DriveStatus())).run())
        assertTrue(drive.items.isEmpty())
    }

    @Test
    fun aWaitingRestore_isNeverBackedUpOver() = runBlocking {
        restorePending = true
        assertEquals(DriveBackupRunner.Outcome.Skipped, runner(MemoryStore(connected)).run())
        assertTrue(drive.items.isEmpty())
    }

    @Test
    fun success_isRecorded_andClearsAnOldError() = runBlocking {
        val store = MemoryStore(connected.copy(lastError = "old", needsSignIn = true))
        assertTrue(runner(store).run() is DriveBackupRunner.Outcome.Done)
        assertEquals(now, store.status.lastSuccessAt)
        assertNull(store.status.lastError)
        assertFalse(store.status.needsSignIn)
    }

    @Test
    fun signedOut_asksForSignIn() = runBlocking {
        drive.failEverything = DriveException(DriveException.Kind.AUTH, "Sign in to Google again to keep backing up.")
        val store = MemoryStore(connected)
        assertTrue(runner(store).run() is DriveBackupRunner.Outcome.Failed)
        assertTrue(store.status.needsSignIn)
    }

    @Test
    fun offline_triesLater_withoutShowingAnError() = runBlocking {
        drive.failEverything = DriveException(DriveException.Kind.OFFLINE, "Couldn't reach Google Drive.")
        val store = MemoryStore(connected)
        assertTrue(runner(store).run() is DriveBackupRunner.Outcome.TryLater)
        assertNull(store.status.lastError)
    }

    @Test
    fun aFullDrive_isShown() = runBlocking {
        drive.failEverything = DriveException(DriveException.Kind.QUOTA, "Your Google Drive is full.")
        val store = MemoryStore(connected)
        runner(store).run()
        assertEquals("Your Google Drive is full.", store.status.lastError)
        assertFalse(store.status.needsSignIn)
    }

    @Test
    fun pdfCopies_runOnlyWhenSomethingChanged() = runBlocking {
        val store = MemoryStore(connected.copy(changedAt = now - 60_000, pdfSyncedAt = now - 120_000))
        assertTrue(runner(store).runPdfCopies() is DriveBackupRunner.PdfOutcome.Updated)
        assertEquals(now, store.status.pdfSyncedAt)
        assertEquals(1, drive.items.values.count { it.mimeType == PdfCopies.PDF_MIME })

        drive.uploads.clear()
        val requestsBefore = drive.items.size
        assertEquals(DriveBackupRunner.PdfOutcome.UpToDate, runner(store).runPdfCopies())
        assertTrue("nothing changed: Drive isn't even asked", drive.uploads.isEmpty())
        assertEquals(requestsBefore, drive.items.size)
    }

    @Test
    fun pdfCopies_notConnected_orOffline() = runBlocking {
        assertEquals(DriveBackupRunner.PdfOutcome.NotConnected, runner(MemoryStore(DriveStatus())).runPdfCopies())
        drive.failEverything = DriveException(DriveException.Kind.OFFLINE, "Couldn't reach Google Drive.")
        val store = MemoryStore(connected)
        assertEquals(DriveBackupRunner.PdfOutcome.TryLater, runner(store).runPdfCopies())
        assertNull("not marked as updated", store.status.pdfSyncedAt)
    }

    @Test
    fun outOfDate_rules() {
        assertTrue("never updated", DriveStatus(email = "a").pdfsOutOfDate)
        assertTrue(DriveStatus(email = "a", changedAt = 20, pdfSyncedAt = 10).pdfsOutOfDate)
        assertFalse(DriveStatus(email = "a", changedAt = 10, pdfSyncedAt = 20).pdfsOutOfDate)
        assertEquals(30, PdfInterval.valid(null))
        assertEquals(30, PdfInterval.valid(7))
        assertEquals(15, PdfInterval.valid(15))
        assertEquals("Every 30 min", PdfInterval.label(30))
        assertEquals("Once a day", PdfInterval.label(PdfInterval.DAILY))
    }

    @Test
    fun ago_words() {
        assertEquals("just now", DriveText.ago(now - 20_000, now))
        assertEquals("3 min ago", DriveText.ago(now - 3 * minute, now))
        assertEquals("1 hour ago", DriveText.ago(now - 61 * minute, now))
        assertEquals("5 hours ago", DriveText.ago(now - 5 * 60 * minute, now))
        assertEquals("yesterday", DriveText.ago(now - day - minute, now))
        assertEquals("4 days ago", DriveText.ago(now - 4 * day, now))
    }

    @Test
    fun summary_words() {
        assertEquals("Backing up…", DriveText.summary(connected, now, running = true))
        assertEquals("Not backed up yet.", DriveText.summary(connected, now, running = false))
        assertEquals(
            "Last backed up 3 min ago · 2.0 MB in Google Drive",
            DriveText.summary(connected.copy(lastSuccessAt = now - 3 * minute, storedBytes = 2L * 1024 * 1024), now, running = false),
        )
        assertEquals("Backup stopped: sign in to Google again.", DriveText.summary(connected.copy(lastError = "x", needsSignIn = true), now, false))
    }

    @Test
    fun home_warnsWhenFailing_orAfterThreeDaysWithoutABackup() {
        assertFalse("not connected: no warning", DriveText.needsAttention(DriveStatus(), now))
        assertFalse(DriveText.needsAttention(connected.copy(lastSuccessAt = now - 2 * day), now))
        assertTrue(DriveText.needsAttention(connected.copy(lastSuccessAt = now - 3 * day), now))
        assertTrue(DriveText.needsAttention(connected.copy(lastError = "Your Google Drive is full."), now))
        assertFalse("connected a day ago, nothing yet: wait", DriveText.needsAttention(connected, now))
        assertTrue(DriveText.needsAttention(connected.copy(connectedAt = now - 4 * day), now))
        assertEquals("No backup to Google Drive since 4 days ago.", DriveText.warning(connected.copy(lastSuccessAt = now - 4 * day), now))
    }
}
