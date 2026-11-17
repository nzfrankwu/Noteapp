package com.nzfrankwu.noteapp.data.backup.drive

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nzfrankwu.noteapp.NoteAppApplication
import java.util.concurrent.TimeUnit

/**
 * The automatic Google Drive backup (PHASE-6.md 6.10), run by WorkManager even when the app is closed:
 * - "soon": 5 minutes after the last change (each change pushes it back),
 * - "daily": once a day,
 * - "now": the Back up now button (any connection, right away).
 * Runs never overlap: the engine holds the backup lock.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as NoteAppApplication).container
        if (inputData.getBoolean(PDFS_ONLY, false)) {
            return when (container.driveBackupRunner.runPdfCopies(::report)) {
                DriveBackupRunner.PdfOutcome.TryLater -> Result.retry()
                else -> Result.success()
            }
        }
        val outcome = container.driveBackupRunner.run(withPdfs = inputData.getBoolean(WITH_PDFS, false), onProgress = ::report)
        return when (outcome) {
            is DriveBackupRunner.Outcome.TryLater -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            else -> Result.success() // failures are recorded for Settings and Home; the next change or day tries again
        }
    }

    /** What Settings shows while this runs: the words, and how far (-1 = can't tell). */
    private fun report(text: String, progress: Float?) {
        setProgressAsync(workDataOf(PROGRESS to (progress ?: -1f), STAGE to text))
    }

    companion object {
        const val SOON = "backup-soon"
        const val DAILY = "backup-daily"
        const val NOW = "backup-now"
        /** "Update PDF copies" every 15/30/60 minutes ([PdfInterval]); the daily run covers "Once a day". */
        const val PDFS = "pdf-copies"
        private const val PDFS_ONLY = "pdfs_only"
        const val PROGRESS = "progress"
        const val STAGE = "stage"
        private const val WITH_PDFS = "with_pdfs"
        val ALL = listOf(SOON, DAILY, NOW, PDFS)
        private const val MAX_ATTEMPTS = 5

        private fun constraints(wifiOnly: Boolean, batteryNotLow: Boolean = true) = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(batteryNotLow)
            .build()

        /** The daily backup (call after connecting, on app start while connected, and when Wi-Fi only changes). */
        fun scheduleDaily(context: Context, wifiOnly: Boolean) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setInputData(workDataOf(WITH_PDFS to true))
                .setConstraints(constraints(wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** The PDF copies every [minutes] while you work (nothing changed = no network at all). */
        fun schedulePdfs(context: Context, minutes: Int, wifiOnly: Boolean) {
            val work = WorkManager.getInstance(context)
            if (minutes >= PdfInterval.DAILY) {
                work.cancelUniqueWork(PDFS)
                return
            }
            val request = PeriodicWorkRequestBuilder<BackupWorker>(minutes.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf(PDFS_ONLY to true))
                .setConstraints(constraints(wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(PDFS, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /**
         * A backup 5 minutes after the last change. A backup that's already uploading is never
         * cancelled (on a slow connection it might never finish): the new one waits until it's done.
         */
        suspend fun soon(context: Context, wifiOnly: Boolean) {
            val work = WorkManager.getInstance(context)
            val states = work.getWorkInfosForUniqueWorkFlow(SOON).first().map { it.state }
            val policy = soonPolicy(states) ?: return
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setConstraints(constraints(wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            work.enqueueUniqueWork(SOON, policy, request)
        }

        /**
         * How to add a "soon" backup, given the ones there now:
         * - one is running and another already waits after it → nothing (the waiting one takes a fresh snapshot);
         * - one is running → add one after it;
         * - otherwise → replace (only a waiting one: pushing it back 5 minutes never cancels an upload).
         */
        fun soonPolicy(states: List<WorkInfo.State>): ExistingWorkPolicy? {
            val running = WorkInfo.State.RUNNING in states
            val waiting = states.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED }
            return when {
                running && waiting -> null
                running -> ExistingWorkPolicy.APPEND_OR_REPLACE
                else -> ExistingWorkPolicy.REPLACE
            }
        }

        /**
         * Back up now: any connection, as soon as possible. Not "expedited": on Android 11 and older
         * that would need a notification of its own.
         */
        fun now(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(workDataOf(WITH_PDFS to true))
                .setConstraints(constraints(wifiOnly = false, batteryNotLow = false))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }

        /** Stops all automatic backups (Disconnect). */
        fun cancelAll(context: Context) {
            val work = WorkManager.getInstance(context)
            ALL.forEach(work::cancelUniqueWork)
        }
    }
}
