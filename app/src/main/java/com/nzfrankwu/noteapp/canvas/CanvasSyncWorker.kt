package com.nzfrankwu.noteapp.canvas

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nzfrankwu.noteapp.NoteAppApplication
import java.util.concurrent.TimeUnit

/**
 * The background Canvas sync, run by Android's WorkManager about every 6 hours when online,
 * even if the app is closed (IDEA.md §3.3). Survives restarts.
 */
class CanvasSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NoteAppApplication
        return when (app.container.canvasSyncRunner.run()) {
            is SyncOutcome.Synced, SyncOutcome.NotSetUp, SyncOutcome.SignInExpired -> Result.success()
            is SyncOutcome.Failed -> Result.retry() // offline or a Canvas hiccup: WorkManager tries again later
        }
    }

    companion object {
        const val UNIQUE_NAME = "canvas-sync"

        /** Starts the background sync (call after signing in, and on app start when signed in). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CanvasSyncWorker>(SyncPolicy.BackgroundInterval.toHours(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Stops the background sync (call when signing out). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
