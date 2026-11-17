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
import androidx.work.WorkManager
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
        val outcome = container.driveBackupRunner.run(withPdfs = inputData.getBoolean(WITH_PDFS, false)) { _, progress ->
            if (progress != null) setProgressAsync(workDataOf(PROGRESS to progress))
        }
        return when (outcome) {
            is DriveBackupRunner.Outcome.TryLater -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            else -> Result.success() // failures are recorded for Settings and Home; the next change or day tries again
        }
    }

    companion object {
        const val SOON = "backup-soon"
        const val DAILY = "backup-daily"
        const val NOW = "backup-now"
        const val PROGRESS = "progress"
        private const val WITH_PDFS = "with_pdfs"
        val ALL = listOf(SOON, DAILY, NOW)
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

        /** A backup 5 minutes after the last change. */
        fun soon(context: Context, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setConstraints(constraints(wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            // Replacing a run that's already uploading is safe: what it sent is reused by the next run.
            WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.REPLACE, request)
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
