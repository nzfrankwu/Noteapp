package com.nzfrankwu.noteapp.data.backup.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Settings → Backup → Google Drive (PHASE-6.md 6.7, 6.10): connecting an account, Back up now,
 * Wi-Fi only, Disconnect, and keeping the automatic backups scheduled. Work runs in [scope]
 * (the app's own), so leaving Settings doesn't stop it.
 */
class DriveConnection(
    private val context: Context,
    private val settings: DriveSettings,
    private val auth: GoogleAuth,
    val backup: DriveBackup,
    private val scope: CoroutineScope,
    /** True when there are no notebooks at all: after connecting, restoring a backup is offered. */
    private val appIsEmpty: suspend () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    sealed interface Connect {
        data object Idle : Connect
        data object Working : Connect
        /** Show Google's screen; then call [userAnswered]. */
        data class NeedsUser(val intent: PendingIntent) : Connect
        /** Connected. [offerRestore] lists Drive's backups when this app is empty (else it's empty). */
        data class Connected(val email: String, val offerRestore: List<DriveBackup.RemoteSnapshot>) : Connect
        data class Failed(val message: String) : Connect
    }

    private val _connect = MutableStateFlow<Connect>(Connect.Idle)
    val connect: StateFlow<Connect> = _connect

    val status: Flow<DriveStatus> = settings.status

    /** A backup job that's running right now: its progress (0..1), or null if none is running. */
    val running: Flow<Float?> = WorkManager.getInstance(context)
        .getWorkInfosFlow(WorkQuery.fromUniqueWorkNames(BackupWorker.ALL))
        .map { infos -> infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.progress?.getFloat(BackupWorker.PROGRESS, 0f) }

    /** "Connect Google Drive" (or "Sign in again"). */
    fun startConnect() {
        if (_connect.value is Connect.Working) return
        _connect.value = Connect.Working
        scope.launch {
            attempt {
                when (val start = auth.start()) {
                    is GoogleAuth.Start.Granted -> connected(start.token)
                    is GoogleAuth.Start.NeedsUser -> _connect.value = Connect.NeedsUser(start.intent)
                }
            }
        }
    }

    /** The screen showed Google's screen for [Connect.NeedsUser] (so it isn't shown twice). */
    fun shown() {
        if (_connect.value is Connect.NeedsUser) _connect.value = Connect.Working
    }

    /** What Google's screen answered. */
    fun userAnswered(ok: Boolean, data: Intent?) {
        if (!ok) {
            _connect.value = Connect.Idle
            return
        }
        _connect.value = Connect.Working
        scope.launch { attempt { connected(auth.finish(data)) } }
    }

    fun dismiss() {
        val c = _connect.value
        if (c is Connect.Failed || c is Connect.Connected) _connect.value = Connect.Idle
    }

    /** Stops backing up to Drive and takes back Noteapp's permission. The backup in Drive stays. */
    fun disconnect() {
        scope.launch {
            val email = settings.email()
            BackupWorker.cancelAll(context)
            settings.disconnect()
            if (email != null) auth.disconnect(email)
            _connect.value = Connect.Idle
        }
    }

    fun backUpNow() = BackupWorker.now(context)

    fun setWifiOnly(on: Boolean) {
        scope.launch {
            settings.setWifiOnly(on)
            reschedule()
        }
    }

    /** "Update PDF copies": every 15/30/60 minutes or once a day ([PdfInterval]). */
    fun setPdfInterval(minutes: Int) {
        scope.launch {
            settings.setPdfInterval(minutes)
            reschedule()
        }
    }

    private suspend fun reschedule() {
        val s = settings.current()
        if (!s.connected) return
        BackupWorker.scheduleDaily(context, s.wifiOnly)
        BackupWorker.schedulePdfs(context, s.pdfIntervalMinutes, s.wifiOnly)
    }

    /** At app start: keep the daily backup scheduled, and back up soon after every change. */
    fun keepBackingUp(changes: Flow<*>) {
        scope.launch { reschedule() }
        scope.launch {
            changes.collect {
                settings.markChanged(now())
                val s = settings.current()
                if (s.connected) BackupWorker.soon(context, s.wifiOnly)
            }
        }
    }

    private suspend fun connected(token: String) {
        val probe = DriveClient(object : DriveTokens {
            override suspend fun token() = token
            override suspend fun invalidate(token: String) = Unit
        })
        val email = probe.accountEmail() ?: throw DriveException(DriveException.Kind.OTHER, "Couldn't tell which Google account this is.")
        settings.connect(email, now())
        reschedule()
        val offer = if (appIsEmpty()) runCatching { backup.snapshots() }.getOrDefault(emptyList()) else emptyList()
        BackupWorker.now(context) // never overwrites a real backup from an empty app (see DriveBackup)
        _connect.value = Connect.Connected(email, offer)
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: DriveException) {
            _connect.value = Connect.Failed(e.message.orEmpty())
        } catch (e: Exception) {
            _connect.value = Connect.Failed("Couldn't connect Google Drive (${e.javaClass.simpleName}).")
        }
    }
}
