package com.nzfrankwu.noteapp

import android.app.Application
import com.nzfrankwu.noteapp.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Runs once when the app starts, before any screen. Holds the [AppContainer]. */
class NoteAppApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** Background work that lives as long as the app process. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // A restore waiting from "Import from export" is swapped in first, before anything opens
        // the database or the settings (PHASE-6.md, "Restore").
        val restore = container.restoreSwap.finishPending()
        container.backupManager.report(restore)
        // PdfBox-Android (PDF export) needs its fonts and resources found once.
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        Notifications.createChannels(this)
        keepRemindersInStep()
        // While signed in to Canvas, keep the 6-hourly background sync scheduled (IDEA.md §3.3).
        appScope.launch { container.canvasAccount.ensureBackgroundSync() }
    }

    /**
     * Whenever any task changes (your edits, Canvas sync, deletes), re-plan the reminder alarms.
     * The short pause groups quick bursts of changes (e.g. a sync touching many tasks) into one.
     */
    private fun keepRemindersInStep() {
        appScope.launch {
            container.taskRepository.observeAllWithCourse().collectLatest { tasks ->
                delay(500)
                container.reminderScheduler.apply(tasks)
            }
        }
    }

    /** Re-plans all reminders right away (after a restart or a time/time-zone change). */
    suspend fun rescheduleRemindersNow() {
        container.reminderScheduler.apply(container.taskRepository.observeAllWithCourse().first())
    }
}
