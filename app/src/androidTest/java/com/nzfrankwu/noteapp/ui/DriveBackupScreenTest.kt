package com.nzfrankwu.noteapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.NoteAppApplication
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.backup.drive.DriveStatus
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.ui.home.HomeScreen
import com.nzfrankwu.noteapp.ui.home.HomeViewModel
import com.nzfrankwu.noteapp.ui.settings.BackupSection
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/**
 * PHASE-6.md 6.7 / 6.10: Settings → Backup → Google Drive with a made-up connected account (no real
 * Google sign-in), and the Home strip when backups stop working.
 */
@RunWith(AndroidJUnit4::class)
class DriveBackupScreenTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = (context.applicationContext as NoteAppApplication).container
    private val settings = container.driveSettings
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        runBlocking {
            settings.disconnect()
            settings.setWifiOnly(true)
        }
    }

    @After
    fun tearDown() {
        runBlocking { settings.disconnect() }
    }

    private fun showBackup() {
        compose.setContent {
            NoteAppTheme { Column { BackupSection(container.backupManager, container.driveConnection) } }
        }
    }

    @Test
    fun notConnected_offersToConnect() {
        showBackup()
        compose.onNodeWithTag("drive-connect").assertIsDisplayed()
        compose.onNodeWithText("Export everything").assertIsDisplayed()
    }

    @Test
    fun connected_showsTheAccount_andWhenItLastWorked() {
        runBlocking {
            settings.connect("test@example.com", now - Duration.ofHours(1).toMillis())
            settings.recordSuccess(now - Duration.ofMinutes(3).toMillis(), 2L * 1024 * 1024)
        }
        showBackup()
        compose.onNodeWithText("Backing up to test@example.com").assertIsDisplayed()
        compose.onNodeWithText("Last backed up 3 min ago · 2.0 MB in Google Drive").assertIsDisplayed()
        compose.onNodeWithTag("drive-back-up-now").assertIsDisplayed()
        compose.onNodeWithTag("drive-restore").assertIsDisplayed()
    }

    @Test
    fun signedOut_showsSignInAgain() {
        runBlocking {
            settings.connect("test@example.com", now)
            settings.recordFailure("Sign in to Google again to keep backing up.", needsSignIn = true)
        }
        showBackup()
        compose.onNodeWithText("Backup stopped: sign in to Google again.").assertIsDisplayed()
        compose.onNodeWithTag("drive-sign-in-again").assertIsDisplayed()
    }

    @Test
    fun wifiOnly_canBeTurnedOff() {
        runBlocking { settings.connect("test@example.com", now) }
        showBackup()
        compose.onNodeWithTag("drive-wifi-only").performClick()
        compose.waitUntil(5_000) { runBlocking { !settings.current().wifiOnly } }
    }

    @Test
    fun pdfCopyInterval_isSaved() {
        runBlocking { settings.connect("test@example.com", now) }
        showBackup()
        compose.onNodeWithText("Update PDF copies").assertIsDisplayed()
        compose.onNodeWithTag("pdf-interval-60").performClick()
        compose.waitUntil(5_000) { runBlocking { settings.current().pdfIntervalMinutes == 60 } }
        runBlocking { settings.setPdfInterval(30) }
    }

    @Test
    fun disconnect_asksFirst_thenStops() {
        runBlocking { settings.connect("test@example.com", now) }
        showBackup()
        compose.onNodeWithTag("drive-disconnect").performClick()
        compose.onNodeWithText("Disconnect Google Drive?").assertIsDisplayed()
        compose.onNodeWithTag("drive-confirm-disconnect").performClick()
        compose.waitUntil(10_000) { runBlocking { !settings.current().connected } }
        compose.onNodeWithTag("drive-connect").assertIsDisplayed()
    }

    @Test
    fun home_warnsWhenBackupsStopped_andNotWhenAllIsWell() {
        val db = Room.inMemoryDatabaseBuilder(context, NoteAppDatabase::class.java).build()
        val status = MutableStateFlow(DriveStatus(email = "test@example.com", connectedAt = now, lastSuccessAt = now))
        val viewModel = HomeViewModel(FolderRepository(db), TaskRepository(db), driveStatus = status)
        var opened = false
        compose.setContent {
            NoteAppTheme { HomeScreen(viewModel, onOpenSettings = { opened = true }, onOpenFolder = {}, onOpenTask = {}, onAddTask = {}) }
        }
        compose.waitForIdle()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasTestTag("banner-backup")).fetchSemanticsNodes().isEmpty())

        status.value = status.value.copy(lastSuccessAt = now - Duration.ofDays(4).toMillis())
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("banner-backup")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("No backup to Google Drive since 4 days ago.").assertIsDisplayed()
        assertFalse(opened)
        compose.onNodeWithText("Open Settings").performClick()
        assertTrue(opened)
        db.close()
    }
}
