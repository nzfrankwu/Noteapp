package com.nzfrankwu.noteapp.canvas

import com.nzfrankwu.noteapp.data.CanvasSettings
import kotlinx.coroutines.flow.first

/** How confirming a sign-in ended. */
sealed interface SignInResult {
    data class SignedIn(val name: String) : SignInResult
    /** Canvas doesn't see a signed-in user yet (e.g. still part-way through the login). */
    data object NotYet : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Connecting Noteapp to your Canvas account (IDEA.md §3.1, §3.5): the school's address,
 * confirming a sign-in done in the in-app browser, and signing out.
 *
 * The sign-in itself happens in the in-app browser; its cookies stay in that browser's private
 * store and are never copied or saved here. This class only saves the address and your name.
 */
class CanvasAccount(
    private val settings: CanvasSettings,
    /** Asks Canvas "who am I?" with the in-app browser's cookies (GET /api/v1/users/self). */
    private val whoAmI: suspend (address: String) -> CanvasUser,
    /** Forgets the in-app browser's cookies and web storage. */
    private val clearBrowser: suspend () -> Unit,
    private val startBackgroundSync: () -> Unit,
    private val stopBackgroundSync: () -> Unit,
) {
    /**
     * Saves the school's Canvas address, e.g. "canvas.example.edu" → "https://canvas.example.edu".
     * Returns the saved address, or null if it isn't a usable https address (nothing is saved).
     * Changing to a different school's address signs out of the old one first.
     */
    suspend fun setAddress(input: String): String? {
        val address = CanvasSignIn.normalizeAddress(input) ?: return null
        val old = settings.address.first()
        if (old != null && old != address) signOut()
        settings.setAddress(address)
        return address
    }

    /**
     * Called when the in-app browser seems to have finished logging in: asks Canvas who is
     * signed in. On success, saves the name, clears any "sign-in expired" flag and starts the
     * background sync.
     */
    suspend fun confirmSignIn(): SignInResult {
        val address = settings.address.first() ?: return SignInResult.Failed("Enter your Canvas address first.")
        return try {
            val user = whoAmI(address)
            settings.setSignedInAs(user.name)
            settings.setSignInExpired(false)
            startBackgroundSync()
            SignInResult.SignedIn(user.name)
        } catch (e: CanvasException.NotSignedIn) {
            SignInResult.NotYet
        } catch (e: CanvasException.SessionExpired) {
            SignInResult.NotYet
        } catch (e: CanvasException) {
            SignInResult.Failed(e.message ?: "Couldn't check the sign-in with Canvas.")
        }
    }

    /**
     * Signs out: stops the background sync and forgets the in-app browser's cookies and storage.
     * Your courses and tasks (including imported ones) are kept, and so is the address.
     */
    suspend fun signOut() {
        stopBackgroundSync()
        clearBrowser()
        settings.setSignedInAs(null)
        settings.setSignInExpired(false)
    }

    /** On app start: keep the background sync scheduled while signed in. */
    suspend fun ensureBackgroundSync() {
        if (settings.signedInAs.first() != null) startBackgroundSync()
    }
}
