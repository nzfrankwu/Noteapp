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

    init {
        // The text boxes' font (Phase 5): the same on screen, in tiles and in exported PDFs.
        com.nzfrankwu.noteapp.data.TextBoxLayout.install(appContext.assets)
    }

    val database: NoteAppDatabase by lazy { NoteAppDatabase.create(appContext) }

    val folderRepository: FolderRepository by lazy { FolderRepository(database) }

    val taskRepository: TaskRepository by lazy { TaskRepository(database) }

    val notebookRepository: NotebookRepository by lazy { NotebookRepository(database) }

    /** Links between tasks and notebooks, and page bookmarks (Phase 4). */
    val linkRepository: com.nzfrankwu.noteapp.data.LinkRepository by lazy {
        com.nzfrankwu.noteapp.data.LinkRepository(database, notebookRepository, files = notebookFiles)
    }

    /** The lasso's Copy/Cut, shared by every notebook while the app runs (PHASE-5.md 5.10). */
    val editorClipboard: com.nzfrankwu.noteapp.ui.notebook.EditorClipboard by lazy { com.nzfrankwu.noteapp.ui.notebook.EditorClipboard() }

    /** The handwriting: one file per page in the app's private files folder (never the cache). */
    val notebookFiles: NotebookFiles by lazy { NotebookFiles(appContext.filesDir) }

    /** First-page pictures for notebook tiles (a cache: drawn again whenever they're missing). */
    val thumbnails: Thumbnails by lazy { Thumbnails(java.io.File(appContext.cacheDir, "thumbnails")) }

    /** Imports PDFs as notebooks (copied into the notebook's folder). */
    val pdfImport: com.nzfrankwu.noteapp.data.PdfImport by lazy {
        com.nzfrankwu.noteapp.data.PdfImport(notebookRepository, notebookFiles, thumbnails)
    }

    /**
     * PDFs or pictures shared into Noteapp and waiting for "Add to Noteapp" (copied into the
     * inbox). Kept here, not in the screen, so turning the tablet doesn't lose it.
     */
    val pendingShare = kotlinx.coroutines.flow.MutableStateFlow<com.nzfrankwu.noteapp.data.SharedFiles?>(null)

    /** PDFs and pictures shared into Noteapp from other apps. */
    val sharedImport: com.nzfrankwu.noteapp.data.SharedImport by lazy {
        com.nzfrankwu.noteapp.data.SharedImport(notebookRepository, notebookFiles, com.nzfrankwu.noteapp.data.ImageImport(notebookFiles), pdfImport)
    }

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

    /** Deletes things from Recently deleted for good (PHASE-6.md 6.4). */
    val notebookPurge: com.nzfrankwu.noteapp.data.NotebookPurge by lazy {
        com.nzfrankwu.noteapp.data.NotebookPurge(database, notebookFiles)
    }

    /** Removes files nothing uses any more (PHASE-6.md 6.5). */
    val fileCleanup: com.nzfrankwu.noteapp.data.FileCleanup by lazy {
        com.nzfrankwu.noteapp.data.FileCleanup(database, appContext.filesDir, inUse = {
            com.nzfrankwu.noteapp.data.OpenNotebooks.ids() + listOfNotNull(editorClipboard.content?.notebookId)
        })
    }

    /** Swaps a checked backup in at start-up (PHASE-6.md, "Restore"). */
    val restoreSwap: com.nzfrankwu.noteapp.data.backup.RestoreSwap by lazy {
        com.nzfrankwu.noteapp.data.backup.RestoreSwap(
            dataDir = appContext.dataDir,
            filesDir = appContext.filesDir,
            databaseFile = appContext.getDatabasePath(NoteAppDatabase.FILE_NAME),
            thumbnailsDir = java.io.File(appContext.cacheDir, "thumbnails"),
        )
    }

    /** A consistent copy of everything a backup needs (PHASE-6.md 6.1). */
    val localSnapshot: com.nzfrankwu.noteapp.data.backup.LocalSnapshot by lazy {
        com.nzfrankwu.noteapp.data.backup.LocalSnapshot(
            database = database,
            databaseFile = appContext.getDatabasePath(NoteAppDatabase.FILE_NAME),
            filesDir = appContext.filesDir,
            appVersion = BuildConfig.VERSION_NAME,
            md5Cache = com.nzfrankwu.noteapp.data.backup.Md5Cache(java.io.File(appContext.cacheDir, "backup-md5.txt")),
        )
    }

    /** "Export everything" and "Import from export" (PHASE-6.md 6.2–6.3). */
    val backupManager: com.nzfrankwu.noteapp.data.backup.BackupManager by lazy {
        com.nzfrankwu.noteapp.data.backup.BackupManager(
            snapshots = localSnapshot,
            swap = restoreSwap,
            stagingDir = java.io.File(appContext.cacheDir, "backup-staging"),
            scope = appScope,
        )
    }

    /** Google Drive backup settings and status (PHASE-6.md 6.10). */
    val driveSettings: com.nzfrankwu.noteapp.data.backup.drive.DriveSettings by lazy {
        com.nzfrankwu.noteapp.data.backup.drive.DriveSettings(appContext)
    }

    /** Google sign-in for Drive (scope drive.file); Play Services keeps the tokens. */
    val googleAuth: com.nzfrankwu.noteapp.data.backup.drive.GoogleAuth by lazy {
        com.nzfrankwu.noteapp.data.backup.drive.GoogleAuth(appContext) { driveSettings.email() }
    }

    /** The full backup to Google Drive, and reading it back (PHASE-6.md 6.9, 6.11). */
    val driveBackup: com.nzfrankwu.noteapp.data.backup.drive.DriveBackup by lazy {
        com.nzfrankwu.noteapp.data.backup.drive.DriveBackup(
            drive = com.nzfrankwu.noteapp.data.backup.drive.DriveClient(googleAuth),
            takeSnapshot = { localSnapshot.take(it) },
            stagingDir = java.io.File(appContext.cacheDir, "drive-staging"),
            listCache = java.io.File(appContext.cacheDir, "drive-lists"),
        )
    }

    /** One run of the automatic Drive backup (the background job uses it). */
    val driveBackupRunner: com.nzfrankwu.noteapp.data.backup.drive.DriveBackupRunner by lazy {
        com.nzfrankwu.noteapp.data.backup.drive.DriveBackupRunner(
            driveSettings, driveBackup, { restoreSwap.isPending },
            pdfCopies = com.nzfrankwu.noteapp.data.backup.drive.PdfCopies(
                drive = com.nzfrankwu.noteapp.data.backup.drive.DriveClient(googleAuth),
                source = com.nzfrankwu.noteapp.data.backup.drive.AppPdfSource(database, notebookFiles),
                scratch = java.io.File(appContext.cacheDir, "pdf-copies"),
            ),
        )
    }

    /** Word/PowerPoint → PDF through Google Drive (PHASE-6.md 6.13). */
    val officeConvert: com.nzfrankwu.noteapp.data.backup.drive.OfficeConvert by lazy {
        com.nzfrankwu.noteapp.data.backup.drive.OfficeConvert(
            drive = com.nzfrankwu.noteapp.data.backup.drive.DriveClient(googleAuth),
            connected = { driveSettings.current().connected },
            scratch = java.io.File(appContext.cacheDir, "convert"),
        )
    }

    /** Settings → Backup → Google Drive. */
    val driveConnection: com.nzfrankwu.noteapp.data.backup.drive.DriveConnection by lazy {
        com.nzfrankwu.noteapp.data.backup.drive.DriveConnection(
            appContext, driveSettings, googleAuth, driveBackup, appScope,
            appIsEmpty = { database.notebookDao().countAll() == 0 },
        )
    }

    /** Runs syncs with the saved settings (used by app open, pull-to-refresh and the background job). */
    val canvasSyncRunner: CanvasSyncRunner by lazy { CanvasSyncRunner(canvasSettings, ::canvasSync) }
}
