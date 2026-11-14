package com.nzfrankwu.noteapp.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nzfrankwu.noteapp.NoteAppApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android forgets every alarm when the tablet restarts, and alarm times can shift when the clock
 * or time zone changes. This puts all reminders back (IDEA.md §2.3, Phase 1 step 4.2).
 * Listens for: restart, app update, time set, time-zone change (see AndroidManifest.xml).
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NoteAppApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.rescheduleRemindersNow()
            } finally {
                pending.finish()
            }
        }
    }
}
