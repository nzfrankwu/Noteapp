package com.nzfrankwu.noteapp.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nzfrankwu.noteapp.MainActivity
import com.nzfrankwu.noteapp.NoteAppApplication
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.domain.ReminderOffset
import com.nzfrankwu.noteapp.domain.ReminderPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/** A reminder alarm went off: shows the notification, if the task still needs it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val taskId = reminderId.substringBeforeLast('/')
        val offsetKey = reminderId.substringAfterLast('/')
        val isTest = offsetKey == ReminderScheduler.TEST_OFFSET
        val offset = ReminderOffset.entries.firstOrNull { it.key == offsetKey }
        if (offset == null && !isTest) return
        val app = context.applicationContext as NoteAppApplication

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Re-check: the task may have been finished or deleted since the alarm was set.
                val item = app.container.taskRepository.observeTask(taskId).first() ?: return@launch
                if (offset == null) {
                    // Debug test reminder: always shown, so the whole path (alarm → notification → tap) can be checked.
                    Notifications.showReminder(context, reminderId, taskId, item.task.title, "Test reminder. Tap to open the task.")
                    return@launch
                }
                if (item.task.status.isFinished || item.task.let { it.targetAt ?: it.dueAt } == null) return@launch
                val text = ReminderPlanner.message(item.task, offset, item.courseCode, ZoneId.systemDefault())
                Notifications.showReminder(context, reminderId, taskId, item.task.title, text)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Notification setup for reminders. */
object Notifications {
    const val CHANNEL_REMINDERS = "task_reminders"
    const val EXTRA_OPEN_TASK_ID = "open_task_id"

    /** Creates the "Task reminders" channel (Android 8+). Safe to call repeatedly. */
    fun createChannels(context: Context) {
        val channel = NotificationChannel(CHANNEL_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminders 1 day and 2 hours before a task is due (or your target date)"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** True if Noteapp may show notifications (Android 13+ asks the user once). */
    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun showReminder(context: Context, reminderId: String, taskId: String, title: String, text: String) {
        if (!canNotify(context)) return
        createChannels(context)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_TASK_ID, taskId)
        val tap = PendingIntent.getActivity(
            context, reminderId.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_folder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission was withdrawn in the meantime: nothing to show.
        }
    }
}
