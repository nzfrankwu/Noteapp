package com.nzfrankwu.noteapp

import android.content.Context
import com.nzfrankwu.noteapp.canvas.CanvasClient
import com.nzfrankwu.noteapp.canvas.CanvasSync
import com.nzfrankwu.noteapp.canvas.CanvasSyncRunner
import com.nzfrankwu.noteapp.canvas.WebViewCanvasCookies
import com.nzfrankwu.noteapp.data.CanvasSettings
import com.nzfrankwu.noteapp.data.FolderRepository
import com.nzfrankwu.noteapp.data.TaskRepository
import com.nzfrankwu.noteapp.data.db.NoteAppDatabase
import com.nzfrankwu.noteapp.reminders.ReminderScheduler

/**
 * Creates the app's long-lived parts once and hands them to screens ("manual dependency
 * injection"). Everything is created lazily: the database file only appears on first use.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: NoteAppDatabase by lazy { NoteAppDatabase.create(appContext) }

    val folderRepository: FolderRepository by lazy { FolderRepository(database) }

    val taskRepository: TaskRepository by lazy { TaskRepository(database) }

    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }

    val canvasSettings: CanvasSettings by lazy { CanvasSettings(appContext) }

    /** The in-app browser's Canvas cookies (read per request, never copied). */
    val canvasCookies: WebViewCanvasCookies by lazy { WebViewCanvasCookies() }

    /** A read-only Canvas client for the school's [address] (from [canvasSettings]). */
    fun canvasClient(address: String): CanvasClient = CanvasClient(address, canvasCookies)

    /** A Canvas sync for the school's [address]. */
    fun canvasSync(address: String): CanvasSync = CanvasSync(database, canvasClient(address))

    /** Runs syncs with the saved settings (used by app open, pull-to-refresh and the background job). */
    val canvasSyncRunner: CanvasSyncRunner by lazy { CanvasSyncRunner(canvasSettings, ::canvasSync) }
}
