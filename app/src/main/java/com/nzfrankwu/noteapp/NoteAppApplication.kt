package com.nzfrankwu.noteapp

import android.app.Application
import com.nzfrankwu.noteapp.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        // The daily tidy-up: Recently deleted after 30 days, unused files (PHASE-6.md 6.4–6.5).
        com.nzfrankwu.noteapp.data.backup.MaintenanceWorker.schedule(this)
        // While Google Drive is connected: a daily backup, and one 5 minutes after the last change.
        container.driveConnection.keepBackingUp(databaseChanges())
        // Search (PHASE-7.md 7.9): read new or changed PDF pages and text boxes in the background.
        keepSearchInStep()
    }

    private fun keepSearchInStep() {
        appScope.launch {
            // Once a few seconds after start-up (let the app start first), then after every change,
            // including changes made while a long first run is still going.
            kotlinx.coroutines.flow.merge(
                kotlinx.coroutines.flow.flow { delay(5_000); emit(Unit) },
                databaseChanges().map { },
            ).collectLatest {
                runCatching { container.noteTextIndexer.run() }
                    .onFailure { if (it !is kotlinx.coroutines.CancellationException) android.util.Log.w("NoteappSearch", "Reading the notes for search failed", it) }
            }
        }
    }

    /** Any change to the notebooks, folders or tasks (page saves mark their page edited, too). */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun databaseChanges() = container.database.invalidationTracker
        .createFlow(*com.nzfrankwu.noteapp.data.db.NoteAppDatabase.TABLES, emitInitialState = false)
        .debounce(10_000)

    /**
     * Whenever any task changes (your edits, Canvas sync, deletes), re-plan the reminder alarms.
     * The short pause groups quick bursts of changes (e.g. a sync touching many tasks) into one.
     */
    private fun keepRemindersInStep() {
        appScope.launch {
            kotlinx.coroutines.flow.combine(
                container.taskRepository.observeAllWithCourse(),
                container.viewSettings.reminderOffsets, // Settings → Reminders (Phase 7, 7.5)
            ) { tasks, offsets -> tasks to offsets }.collectLatest { (tasks, offsets) ->
                delay(500)
                container.reminderScheduler.apply(tasks, offsets)
            }
        }
    }

    /** Re-plans all reminders right away (after a restart or a time/time-zone change). */
    suspend fun rescheduleRemindersNow() {
        container.reminderScheduler.apply(
            container.taskRepository.observeAllWithCourse().first(),
            container.viewSettings.reminderOffsets.first(),
        )
    }
}
