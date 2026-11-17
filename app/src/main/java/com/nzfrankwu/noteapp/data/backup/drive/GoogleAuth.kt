package com.nzfrankwu.noteapp.data.backup.drive

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google sign-in for the Drive backup (PHASE-6.md 6.7), through Play Services' AuthorizationClient
 * with the narrow `drive.file` permission: Noteapp only ever sees files it made itself.
 *
 * Play Services keeps the tokens. Noteapp never stores, logs or prints them; it keeps only the
 * account's email address, to ask for that same account again in the background.
 */
class GoogleAuth(
    context: Context,
    /** The connected account's email (from Settings), or null if not connected. */
    private val connectedEmail: suspend () -> String?,
) : DriveTokens {
    private val client = Identity.getAuthorizationClient(context.applicationContext)

    sealed interface Start {
        /** Allowed already (e.g. connected before): here's a token. */
        data class Granted(val token: String) : Start
        /** Google needs to ask the person (pick an account, allow Noteapp): show this. */
        data class NeedsUser(val intent: PendingIntent) : Start
    }

    /** Step 1 of "Connect Google Drive". Throws [DriveException]. */
    suspend fun start(): Start {
        val result = authorize(request(email = null))
        return if (result.hasResolution()) {
            Start.NeedsUser(result.pendingIntent ?: throw DriveException(DriveException.Kind.AUTH, "Google sign-in didn't start. Try again."))
        } else {
            Start.Granted(result.accessToken ?: throw notAllowed())
        }
    }

    /** Step 2, with what Google's screen sent back. Returns a token. Throws [DriveException]. */
    fun finish(data: Intent?): String = try {
        client.getAuthorizationResultFromIntent(data).accessToken ?: throw notAllowed()
    } catch (e: ApiException) {
        throw e.toDriveException()
    }

    /** A token for background backups, without showing anything. */
    override suspend fun token(): String {
        val email = connectedEmail() ?: throw DriveException(DriveException.Kind.AUTH, "Connect Google Drive in Settings to back up.")
        val result = authorize(request(email))
        if (result.hasResolution()) throw DriveException(DriveException.Kind.AUTH, "Sign in to Google again to keep backing up.")
        return result.accessToken ?: throw notAllowed()
    }

    override suspend fun invalidate(token: String) {
        runCatching { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    /** "Disconnect": takes back Noteapp's Drive permission for [email]. The backup in Drive stays. */
    suspend fun disconnect(email: String) {
        try {
            client.revokeAccess(
                RevokeAccessRequest.builder().setAccount(Account(email, ACCOUNT_TYPE)).setScopes(listOf(Scope(SCOPE))).build(),
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or already gone: Noteapp forgets the account anyway.
        }
    }

    private fun request(email: String?): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .apply { if (email != null) setAccount(Account(email, ACCOUNT_TYPE)) }
        .build()

    private suspend fun authorize(request: AuthorizationRequest): AuthorizationResult = try {
        client.authorize(request).await()
    } catch (e: ApiException) {
        throw e.toDriveException()
    }

    private fun notAllowed() = DriveException(DriveException.Kind.AUTH, "Noteapp wasn't allowed to use Google Drive.")

    private fun ApiException.toDriveException(): DriveException = when (statusCode) {
        CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT ->
            DriveException(DriveException.Kind.OFFLINE, "Couldn't reach Google. Check the internet connection.", this)
        CommonStatusCodes.CANCELED -> DriveException(DriveException.Kind.AUTH, "Google sign-in was cancelled.", this)
        CommonStatusCodes.DEVELOPER_ERROR ->
            DriveException(DriveException.Kind.OTHER, "Google sign-in isn't set up for this copy of Noteapp yet.", this)
        else -> DriveException(DriveException.Kind.AUTH, "Google sign-in didn't work (code $statusCode).", this)
    }

    companion object {
        /** Only files Noteapp creates (IDEA.md §2.9). */
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val ACCOUNT_TYPE = "com.google"
    }
}

/** Waits for a Play Services task without another library. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
