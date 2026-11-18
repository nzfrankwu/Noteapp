package com.nzfrankwu.noteapp.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.nzfrankwu.noteapp.data.db.TaskWithCourse
import com.nzfrankwu.noteapp.domain.PlannedReminder
import com.nzfrankwu.noteapp.domain.ReminderPlanner
import java.time.Clock
import java.time.ZoneId

/**
 * Keeps the tablet's alarms in step with the tasks (IDEA.md §2.3 reminders).
 *
 * [apply] is called with the full task list whenever tasks change (see NoteAppApplication), and
 * after a restart or time/time-zone change ([RescheduleReceiver]). It works out every reminder
 * that should exist ([ReminderPlanner]), sets those alarms (setting one again just moves it),
 * and cancels any it set before that are no longer wanted (task finished, deleted, re-dated).
 *
 * Alarms are inexact-while-idle: they may arrive a few minutes late in battery saving, but need no
 * special "exact alarm" permission. The IDs of set alarms are remembered so they can be cancelled.
 */
class ReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    storeName: String = "reminders", // tests use their own store so they can't clash with the app
) {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val prefs = appContext.getSharedPreferences(storeName, Context.MODE_PRIVATE)

    /**
     * Makes the alarms match [tasks]. Safe to call as often as needed. Tasks of archived courses
     * get no reminders (their alarms are cancelled), so archiving a course silences it.
     */
    @Synchronized
    fun apply(
        tasks: List<TaskWithCourse>,
        offsets: Set<com.nzfrankwu.noteapp.domain.ReminderOffset> = com.nzfrankwu.noteapp.domain.ReminderOffset.DEFAULT,
    ) {
        val now = clock.instant()
        val wanted = tasks
            .filterNot { it.isCourseArchived }
            .flatMap { ReminderPlanner.plan(it.task.id, it.task, now, zone(), offsets) }
        val wantedIds = wanted.map { it.id }.toSet()

        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        (previous - wantedIds).forEach { cancel(it) }
        wanted.forEach { schedule(it) }

        prefs.edit { putStringSet(KEY_SCHEDULED, wantedIds) }
    }

    /**
     * Debug builds only: a test reminder for [taskId] in [delayMillis] (default 1 minute). It has
     * its own ID ("<task>/test"), so it never replaces the task's real reminders.
     */
    fun scheduleTest(taskId: String, delayMillis: Long = 60_000) {
        val intent = pendingIntent("$taskId/$TEST_OFFSET", PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, clock.millis() + delayMillis, intent)
    }

    /** True if an alarm is currently set for this reminder ID (used by tests and diagnostics). */
    fun isScheduled(reminderId: String): Boolean = pendingIntent(reminderId, PendingIntent.FLAG_NO_CREATE) != null

    private fun schedule(reminder: PlannedReminder) {
        val intent = pendingIntent(reminder.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at.toEpochMilli(), intent)
    }

    private fun cancel(reminderId: String) {
        pendingIntent(reminderId, PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(reminderId: String, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(appContext, 0, intentFor(reminderId), flag or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        private const val KEY_SCHEDULED = "scheduled_ids"
        const val EXTRA_REMINDER_ID = "reminder_id"

        /** The offset part of a test reminder's ID (see [scheduleTest]). */
        const val TEST_OFFSET = "test"

        /**
         * One intent per reminder. Its data URI (noteapp://reminder/<task>/<offset>) makes each
         * reminder's alarm distinct, so setting it again replaces it instead of adding another.
         */
        fun intentFor(reminderId: String): Intent {
            val taskId = reminderId.substringBeforeLast('/')
            val offset = reminderId.substringAfterLast('/')
            return Intent()
                .setClassName("com.nzfrankwu.noteapp", ReminderReceiver::class.java.name)
                .setData(Uri.Builder().scheme("noteapp").authority("reminder").appendPath(taskId).appendPath(offset).build())
                .putExtra(EXTRA_REMINDER_ID, reminderId)
        }
    }
}
