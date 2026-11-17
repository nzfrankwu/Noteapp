package com.nzfrankwu.noteapp.data.backup.drive

import com.nzfrankwu.noteapp.data.backup.BackupText
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/** Where the Google Drive backup stands (kept in the `backup_settings` store, never backed up itself). */
data class DriveStatus(
    /** The connected Google account, or null if Drive backup is off. */
    val email: String? = null,
    val connectedAt: Long? = null,
    /** Automatic backups wait for Wi-Fi (Back up now uses any connection). */
    val wifiOnly: Boolean = true,
    val lastSuccessAt: Long? = null,
    /** Why the last backup failed (a message for the user), cleared by the next success. */
    val lastError: String? = null,
    /** The last failure needs the user to sign in to Google again. */
    val needsSignIn: Boolean = false,
    /** What the backup takes in Drive, after the last success. */
    val storedBytes: Long = 0,
    /** How often the PDF copies are brought up to date while you work ([PdfInterval]). */
    val pdfIntervalMinutes: Int = PdfInterval.DEFAULT,
    /** When notebooks, folders or tasks last changed (null: not since this was added). */
    val changedAt: Long? = null,
    /** When the PDF copies were last brought up to date (the start of that run). */
    val pdfSyncedAt: Long? = null,
) {
    val connected: Boolean get() = email != null

    /** True when something changed since the PDF copies were last updated. */
    val pdfsOutOfDate: Boolean get() = pdfSyncedAt == null || (changedAt != null && changedAt > pdfSyncedAt)
}

/** The choices for "Update PDF copies" (minutes). Android runs background work at most every 15 minutes. */
object PdfInterval {
    const val DAILY = 24 * 60
    const val DEFAULT = 30
    val CHOICES = listOf(15, 30, 60, DAILY)

    fun label(minutes: Int): String = when (minutes) {
        DAILY -> "Once a day"
        60 -> "Every hour"
        else -> "Every $minutes min"
    }

    /** Only these values are used; anything else falls back to the default. */
    fun valid(minutes: Int?): Int = minutes?.takeIf { it in CHOICES } ?: DEFAULT
}

/** Records how backups go (the app's DataStore; a fake in tests). */
interface DriveStatusStore {
    suspend fun current(): DriveStatus
    suspend fun recordSuccess(at: Long, storedBytes: Long)
    suspend fun recordFailure(message: String, needsSignIn: Boolean)
    suspend fun recordPdfSync(at: Long)
}

/** The words for the backup status in Settings and on Home (PHASE-6.md 6.10). */
object DriveText {
    /** No backup for this long → Home shows a warning. */
    val WARN_AFTER: Duration = Duration.ofDays(3)

    fun ago(then: Long, now: Long): String {
        val minutes = Duration.ofMillis((now - then).coerceAtLeast(0)).toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> (minutes / 60).let { if (it == 1L) "1 hour ago" else "$it hours ago" }
            else -> (minutes / (24 * 60)).let { if (it == 1L) "yesterday" else "$it days ago" }
        }
    }

    /** The line under "Backing up to …" in Settings. */
    fun summary(s: DriveStatus, now: Long, running: Boolean): String = when {
        running -> "Backing up…"
        s.needsSignIn -> "Backup stopped: sign in to Google again."
        s.lastError != null -> "The last backup didn't finish: ${s.lastError}"
        s.lastSuccessAt != null -> "Last backed up ${ago(s.lastSuccessAt, now)} · ${BackupText.size(s.storedBytes)} in Google Drive"
        else -> "Not backed up yet."
    }

    /** True when Home should warn: connected, and failing or nothing backed up for [WARN_AFTER]. */
    fun needsAttention(s: DriveStatus, now: Long): Boolean {
        if (!s.connected) return false
        if (s.needsSignIn || s.lastError != null) return true
        val since = s.lastSuccessAt ?: s.connectedAt ?: return false
        return now - since >= WARN_AFTER.toMillis()
    }

    /** The Home warning (only when [needsAttention]). */
    fun warning(s: DriveStatus, now: Long): String = when {
        s.needsSignIn -> "Google Drive backup stopped. Sign in to Google again."
        s.lastError != null -> "Backing up to Google Drive isn't working."
        s.lastSuccessAt == null -> "Nothing has been backed up to Google Drive yet."
        else -> "No backup to Google Drive since ${ago(s.lastSuccessAt, now)}."
    }
}

/**
 * One backup run for the background job (PHASE-6.md 6.10): skips when Drive isn't connected or a
 * restore is waiting, and records the outcome for Settings and Home.
 */
class DriveBackupRunner(
    private val store: DriveStatusStore,
    private val backup: DriveBackup,
    private val restorePending: () -> Boolean,
    /** The readable PDF copies, refreshed after the backup on daily and Back up now runs. */
    private val pdfCopies: PdfCopies? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Outcome {
        data object NotConnected : Outcome
        /** A restore is waiting, or this app is empty while Drive holds a backup. */
        data object Skipped : Outcome
        data class Done(val result: DriveBackup.Result) : Outcome
        /** No connection or Drive busy: the job tries again later. Not shown as an error. */
        data class TryLater(val message: String) : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun run(withPdfs: Boolean = false, onProgress: (String, Float?) -> Unit = { _, _ -> }): Outcome {
        if (!store.current().connected) return Outcome.NotConnected
        if (restorePending()) return Outcome.Skipped
        return try {
            val result = backup.backUp(onProgress) ?: return Outcome.Skipped
            store.recordSuccess(now(), result.storedBytes)
            if (withPdfs && pdfCopies != null) {
                onProgress("Updating the PDF copies…", null)
                // The copies are for reading; the backup above already succeeded, so trouble here
                // only waits for the next run.
                runCatching { syncPdfs(onProgress) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
            Outcome.Done(result)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: DriveException) {
            when (e.kind) {
                DriveException.Kind.OFFLINE -> Outcome.TryLater(e.message.orEmpty())
                DriveException.Kind.AUTH -> {
                    store.recordFailure(e.message.orEmpty(), needsSignIn = true)
                    Outcome.Failed(e.message.orEmpty())
                }
                else -> {
                    store.recordFailure(e.message.orEmpty(), needsSignIn = false)
                    Outcome.Failed(e.message.orEmpty())
                }
            }
        } catch (e: Exception) {
            val message = if (e is com.nzfrankwu.noteapp.data.backup.BackupException) e.message.orEmpty()
            else "Something went wrong (${e.javaClass.simpleName})."
            store.recordFailure(message, needsSignIn = false)
            Outcome.Failed(message)
        }
    }

    /**
     * The PDF copies on their own (the "Update PDF copies" job): only when something changed since
     * the last update, so a quiet tablet never even contacts Google.
     */
    suspend fun runPdfCopies(onProgress: (String, Float?) -> Unit = { _, _ -> }): PdfOutcome {
        val status = store.current()
        if (!status.connected || pdfCopies == null) return PdfOutcome.NotConnected
        if (restorePending() || !status.pdfsOutOfDate) return PdfOutcome.UpToDate
        return try {
            PdfOutcome.Updated(syncPdfs(onProgress))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: DriveException) {
            when (e.kind) {
                DriveException.Kind.OFFLINE -> PdfOutcome.TryLater
                DriveException.Kind.AUTH -> {
                    // Settings and Home must say so now, not only after the next full backup.
                    store.recordFailure(e.message.orEmpty(), needsSignIn = true)
                    PdfOutcome.Failed(e.message.orEmpty())
                }
                else -> PdfOutcome.Failed(e.message.orEmpty())
            }
        } catch (e: Exception) {
            PdfOutcome.Failed("Something went wrong (${e.javaClass.simpleName}).")
        }
    }

    sealed interface PdfOutcome {
        data object NotConnected : PdfOutcome
        data object UpToDate : PdfOutcome
        data class Updated(val result: PdfCopies.Result) : PdfOutcome
        data object TryLater : PdfOutcome
        data class Failed(val message: String) : PdfOutcome
    }

    /** Brings the copies up to date; changes made while it runs are caught by the next run. */
    private suspend fun syncPdfs(onProgress: (String, Float?) -> Unit): PdfCopies.Result {
        val pdfs = checkNotNull(pdfCopies)
        val started = now()
        // No storage lock here: AppPdfSource.export holds it for one notebook at a time (Phase 7, 7.2).
        val result = pdfs.sync { done, total ->
            onProgress("Updating the PDF copies ($done of $total)…", if (total > 0) done.toFloat() / total else null)
        }
        store.recordPdfSync(started)
        return result
    }
}
