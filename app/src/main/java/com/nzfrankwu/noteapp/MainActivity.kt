package com.nzfrankwu.noteapp

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: after rotation the task page is already on the back stack.
        if (savedInstanceState == null) takeRequests(intent)
        // Each time the app comes to the front: sync with Canvas if the last sync was over 15
        // minutes ago (does nothing when Canvas isn't connected).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as NoteAppApplication).container.canvasSyncRunner.runIfDue()
            }
        }
        val courseDao = (application as NoteAppApplication).container.database.courseDao()
        setContent {
            // Course colours follow the order courses were first seen (see CourseColorKey).
            val courses by courseDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
            val seen = remember(courses) { courses.map { CourseSeen(it.code, it.createdAt) } }
            NoteAppTheme(courses = seen) {
                NoteAppRoot(openTaskRequest = openTaskRequest, shareRequest = shareRequest)
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

    private fun takeRequests(intent: Intent?) {
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
}
