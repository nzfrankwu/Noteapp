package com.nzfrankwu.noteapp

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.nzfrankwu.noteapp.domain.CourseSeen
import com.nzfrankwu.noteapp.reminders.Notifications
import com.nzfrankwu.noteapp.ui.NoteAppRoot
import com.nzfrankwu.noteapp.ui.notebook.PenButtonKeyEvents
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.flow.MutableStateFlow

/** Text another app shared with Noteapp. */
data class SharedText(val text: String?, val subject: String?)

class MainActivity : ComponentActivity() {
    /**
     * A task to open, from a tapped reminder. The UI opens it and then clears this.
     * (The activity is singleTop, so a tap while the app is open arrives in [onNewIntent].)
     */
    private val openTaskRequest = MutableStateFlow<String?>(null)

    /** Text shared into Noteapp from another app (e.g. a link), to become a new task. */
    private val shareRequest = MutableStateFlow<SharedText?>(null)

    /** PDFs or pictures shared into Noteapp, already copied into the inbox (PHASE-3.md 3.8). */
    private val fileShareRequest get() = (application as NoteAppApplication).container.pendingShare

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: after rotation the task page is already on the back stack.
        if (savedInstanceState == null) {
            // Copies left in the inbox by a share that was never finished (the app was closed).
            if (!isFileShare(intent) && fileShareRequest.value == null) {
                java.io.File(filesDir, com.nzfrankwu.noteapp.data.SharedImport.INBOX_DIR).deleteRecursively()
            }
            takeRequests(intent)
        }
        // Each time the app comes to the front: sync with Canvas if the last sync was over 15
        // minutes ago (does nothing when Canvas isn't connected).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as NoteAppApplication).container.canvasSyncRunner.runIfDue()
            }
        }
        val courseDao = (application as NoteAppApplication).container.database.courseDao()
        val appSettings = (application as NoteAppApplication).container.viewSettings
        // Read once before the first frame (a tiny file), so a forced light/dark never flashes.
        val firstTheme = kotlinx.coroutines.runBlocking { appSettings.themeMode.first() }
        setContent {
            // Course colours follow the order courses were first seen (see CourseColorKey).
            val courses by courseDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
            val seen = remember(courses) { courses.map { CourseSeen(it.code, it.createdAt) } }
            // Light, dark or like the tablet (Settings, Phase 7, 7.6).
            val themeMode by appSettings.themeMode.collectAsStateWithLifecycle(initialValue = firstTheme)
            val dark = themeMode.isDark(androidx.compose.foundation.isSystemInDarkTheme())
            androidx.compose.runtime.LaunchedEffect(dark) {
                // Status and navigation bar icons that suit the chosen look, not the tablet's.
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(transparent)
                    else androidx.activity.SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(transparent)
                    else androidx.activity.SystemBarStyle.light(transparent, transparent),
                )
            }
            NoteAppTheme(darkTheme = dark, courses = seen) {
                NoteAppRoot(openTaskRequest = openTaskRequest, shareRequest = shareRequest, fileShareRequest = fileShareRequest)
            }
        }
    }

    /** Pen side buttons that arrive as keys go to the open notebook (see PenButtonKeyEvents). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        PenButtonKeyEvents.handle(event) || super.dispatchKeyEvent(event)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeRequests(intent)
    }

    private fun isFileShare(intent: Intent?): Boolean {
        val type = intent?.type ?: return false
        val office = type in com.nzfrankwu.noteapp.data.backup.drive.OfficeConvert.MIME_TYPES
        return (intent.action == Intent.ACTION_SEND && (type == PDF_TYPE || office || type.startsWith("image/"))) ||
            (intent.action == Intent.ACTION_SEND_MULTIPLE && type.startsWith("image/")) ||
            (intent.action == Intent.ACTION_VIEW && (type == PDF_TYPE || office))
    }

    /**
     * A PDF or pictures shared into Noteapp: copy them into the private inbox straight away (the
     * other app's permission to read them can lapse), then ask where they go.
     */
    private fun takeFileShare(intent: Intent) {
        val uris: List<android.net.Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> androidx.core.content.IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java).orEmpty()
            else -> listOfNotNull(intent.data)
        }
        // A Word/PowerPoint file goes the PDF way (into a folder you pick), converted on the way in.
        val officeType = com.nzfrankwu.noteapp.data.backup.drive.OfficeConvert.officeType(intent.type, null)
        val pdf = intent.type == PDF_TYPE || officeType != null
        val extension = when (officeType) {
            null -> if (pdf) "pdf" else "img"
            else -> com.nzfrankwu.noteapp.data.backup.drive.OfficeConvert.extensionFor(officeType)
        }
        intent.action = Intent.ACTION_MAIN // so it isn't handled twice
        if (uris.isEmpty()) return
        val inbox = java.io.File(filesDir, com.nzfrankwu.noteapp.data.SharedImport.INBOX_DIR)
        lifecycleScope.launch {
            val copies = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                uris.take(MAX_SHARED_FILES).mapNotNull { uri ->
                    val name = runCatching {
                        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }.getOrNull()
                    com.nzfrankwu.noteapp.data.SharedImport.copyToInbox(inbox, extension) {
                        contentResolver.openInputStream(uri)
                    }?.let { com.nzfrankwu.noteapp.data.InboxFile(it, name) }
                }
            }
            if (copies.isEmpty()) {
                android.widget.Toast.makeText(this@MainActivity, "Noteapp couldn't read that file.", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            fileShareRequest.value = com.nzfrankwu.noteapp.data.SharedFiles(
                if (pdf) com.nzfrankwu.noteapp.data.SharedKind.PDF else com.nzfrankwu.noteapp.data.SharedKind.PICTURES,
                if (pdf) copies.take(1) else copies,
            )
        }
    }

    private fun takeRequests(intent: Intent?) {
        if (intent != null && isFileShare(intent)) {
            takeFileShare(intent)
            return
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            shareRequest.value = SharedText(
                text = intent.getStringExtra(Intent.EXTRA_TEXT),
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            )
            intent.action = Intent.ACTION_MAIN // so it isn't handled twice
            return
        }
        val taskId = intent?.getStringExtra(Notifications.EXTRA_OPEN_TASK_ID) ?: return
        intent.removeExtra(Notifications.EXTRA_OPEN_TASK_ID) // so it isn't handled twice
        openTaskRequest.value = taskId
    }

    private companion object {
        const val PDF_TYPE = "application/pdf"

        /** More pictures than this at once are left out (one page each). */
        const val MAX_SHARED_FILES = 50
    }
}
