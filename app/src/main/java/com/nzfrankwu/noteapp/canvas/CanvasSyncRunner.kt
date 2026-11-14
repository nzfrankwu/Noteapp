package com.nzfrankwu.noteapp.canvas

import com.nzfrankwu.noteapp.data.CanvasSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** When Canvas should sync (IDEA.md §3.3). */
object SyncPolicy {
    /** Opening the app syncs only if the last sync was this long ago (don't hammer the server). */
    val MinGapOnAppOpen: Duration = Duration.ofMinutes(15)

    /** How often the background sync runs (WorkManager may shift it a little). */
    val BackgroundInterval: Duration = Duration.ofHours(6)

    fun shouldSyncOnAppOpen(lastSyncedAt: Instant?, now: Instant): Boolean =
        lastSyncedAt == null || Duration.between(lastSyncedAt, now) >= MinGapOnAppOpen
}

/** How a sync attempt ended, for the screen (and the background job). */
sealed interface SyncOutcome {
    data class Synced(val summary: SyncSummary) : SyncOutcome
    /** No Canvas address or not signed in: nothing to do. */
    data object NotSetUp : SyncOutcome
    /** Canvas says the sign-in expired: data is kept, the banner asks to sign in again. */
    data object SignInExpired : SyncOutcome
    /** Couldn't sync this time (offline, Canvas error...). Data is kept; try again later. */
    data class Failed(val message: String) : SyncOutcome
}

/**
 * Runs one Canvas sync with the saved settings, and records the result: "last synced" on
 * success, the expired-sign-in flag when Canvas rejects the session. Only one sync runs at a time
 * (a pull-to-refresh during a background sync just waits for it).
 */
class CanvasSyncRunner(
    private val settings: CanvasSettings,
    private val syncFor: (address: String) -> CanvasSync,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val oneAtATime = Mutex()

    suspend fun run(): SyncOutcome = oneAtATime.withLock {
        val address = settings.address.first()
        if (address == null || settings.signedInAs.first() == null) return@withLock SyncOutcome.NotSetUp

        try {
            val summary = syncFor(address).sync()
            settings.setLastSyncedAt(clock.instant())
            settings.setSignInExpired(false)
            SyncOutcome.Synced(summary)
        } catch (e: CanvasException.SessionExpired) {
            settings.setSignInExpired(true)
            SyncOutcome.SignInExpired
        } catch (e: CanvasException.NotSignedIn) {
            settings.setSignInExpired(true)
            SyncOutcome.SignInExpired
        } catch (e: CanvasException) {
            SyncOutcome.Failed(e.message ?: "Canvas sync failed")
        }
    }

    /** For app open: sync only if the last one was over 15 minutes ago. */
    suspend fun runIfDue(): SyncOutcome? =
        if (SyncPolicy.shouldSyncOnAppOpen(settings.lastSyncedAt.first(), clock.instant())) run() else null
}
