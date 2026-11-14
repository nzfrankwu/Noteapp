package com.nzfrankwu.noteapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nzfrankwu.noteapp.reminders.Notifications
import com.nzfrankwu.noteapp.ui.NoteAppRoot
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /**
     * A task to open, from a tapped reminder. The UI opens it and then clears this.
     * (The activity is singleTop, so a tap while the app is open arrives in [onNewIntent].)
     */
    private val openTaskRequest = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: after rotation the task page is already on the back stack.
        if (savedInstanceState == null) takeOpenTaskRequest(intent)
        setContent {
            NoteAppTheme {
                NoteAppRoot(openTaskRequest = openTaskRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeOpenTaskRequest(intent)
    }

    private fun takeOpenTaskRequest(intent: Intent?) {
        val taskId = intent?.getStringExtra(Notifications.EXTRA_OPEN_TASK_ID) ?: return
        intent.removeExtra(Notifications.EXTRA_OPEN_TASK_ID) // so it isn't handled twice
        openTaskRequest.value = taskId
    }
}
