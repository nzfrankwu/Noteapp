package com.nzfrankwu.noteapp

import android.content.Context
import com.nzfrankwu.noteapp.canvas.CanvasAccount
import com.nzfrankwu.noteapp.canvas.CanvasClient
import com.nzfrankwu.noteapp.canvas.CanvasSync
import com.nzfrankwu.noteapp.canvas.CanvasSyncRunner
import com.nzfrankwu.noteapp.canvas.CanvasSyncWorker
import com.nzfrankwu.noteapp.canvas.FakeCanvas
import com.nzfrankwu.noteapp.canvas.WebViewCanvasCookies
import com.nzfrankwu.noteapp.data.CanvasSettings
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.InkSettings
import com.nzfrankwu.noteapp.data.NotebookFiles
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.QuickAdd
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.Thumbnails
import com.nzfrankwu.noteapp.data.ViewSettings
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * Creates the app's long-lived parts once and hands them to screens ("manual dependency
 * injection"). Everything is created lazily: the database file only appears on first use.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: NoteAppDatabase by lazy { NoteAppDatabase.create(appContext) }

    val folderRepository: FolderRepository by lazy { FolderRepository(database) }

    val taskRepository: TaskRepository by lazy { TaskRepository(database) }

    val notebookRepository: NotebookRepository by lazy { NotebookRepository(database) }

    /** The handwriting: one file per page in the app's private files folder (never the cache). */
    val notebookFiles: NotebookFiles by lazy { NotebookFiles(appContext.filesDir) }

    /** First-page pictures for notebook tiles (a cache: drawn again whenever they're missing). */
    val thumbnails: Thumbnails by lazy { Thumbnails(java.io.File(appContext.cacheDir, "thumbnails")) }

    /** The notebook toolbar's remembered choices. */
    val inkSettings: InkSettings by lazy { InkSettings(appContext) }

    /** Work that must finish even after its screen closes (saving notebook pages). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Turns links shared from other apps into prefilled New task forms. */
    val quickAdd: QuickAdd by lazy { QuickAdd(database) { canvasSettings.address.first() } }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }

    val canvasSettings: CanvasSettings by lazy { CanvasSettings(appContext) }

    /** Remembered view choices, e.g. the last home tab. */
    val viewSettings: ViewSettings by lazy { ViewSettings(appContext) }

    /** The in-app browser's Canvas cookies (read per request, never copied). */
    val canvasCookies: WebViewCanvasCookies by lazy { WebViewCanvasCookies() }

    /** A read-only Canvas client for the school's [address] (from [canvasSettings]). */
    fun canvasClient(address: String): CanvasClient = CanvasClient(address, canvasCookies)

    /** A Canvas sync for the school's [address]. (Debug builds: the fake Canvas's address uses [FakeCanvas].) */
    fun canvasSync(address: String): CanvasSync =
        CanvasSync(database, if (useFake(address)) FakeCanvas() else canvasClient(address))

    private fun useFake(address: String) = BuildConfig.DEBUG && FakeCanvas.isFake(address)

    /** Connecting to Canvas: address, confirming a sign-in, signing out. */
    val canvasAccount: CanvasAccount by lazy {
        CanvasAccount(
            settings = canvasSettings,
            whoAmI = { address -> if (useFake(address)) FakeCanvas().user else canvasClient(address).getSelf() },
            clearBrowser = { canvasCookies.signOut() },
            startBackgroundSync = { CanvasSyncWorker.schedule(appContext) },
            stopBackgroundSync = { CanvasSyncWorker.cancel(appContext) },
        )
    }

    /** Runs syncs with the saved settings (used by app open, pull-to-refresh and the background job). */
    val canvasSyncRunner: CanvasSyncRunner by lazy { CanvasSyncRunner(canvasSettings, ::canvasSync) }
}
