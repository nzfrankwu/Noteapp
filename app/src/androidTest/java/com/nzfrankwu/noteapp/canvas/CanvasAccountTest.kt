package com.nzfrankwu.noteapp.canvas

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.data.CanvasSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Setting the address, confirming a sign-in and signing out (no real Canvas or browser). */
@RunWith(AndroidJUnit4::class)
class CanvasAccountTest {
    private val settings = CanvasSettings(InstrumentationRegistry.getInstrumentation().targetContext)
    private var whoAmIResult: () -> CanvasUser = { CanvasUser(7, "Test Student") }
    private var browserCleared = 0
    private var backgroundOn = false
    private val account = CanvasAccount(
        settings = settings,
        whoAmI = { whoAmIResult() },
        clearBrowser = { browserCleared++ },
        startBackgroundSync = { backgroundOn = true },
        stopBackgroundSync = { backgroundOn = false },
    )

    private suspend fun reset() {
        settings.setAddress(null)
        settings.setSignedInAs(null)
        settings.setSignInExpired(false)
    }

    @Before
    fun setUp() = runBlocking { reset() }

    @After
    fun tearDown() = runBlocking { reset() }

    @Test
    fun address_isTidied_andHttpIsRefused() = runBlocking {
        assertEquals("https://canvas.example.edu", account.setAddress("canvas.example.edu/courses/1"))
        assertEquals("https://canvas.example.edu", settings.address.first())
        assertNull(account.setAddress("http://canvas.example.edu"))
        assertEquals("https://canvas.example.edu", settings.address.first()) // unchanged
    }

    @Test
    fun confirmedSignIn_savesTheName_clearsExpired_andStartsBackgroundSync() = runBlocking {
        account.setAddress("canvas.example.edu")
        settings.setSignInExpired(true)
        assertEquals(SignInResult.SignedIn("Test Student"), account.confirmSignIn())
        assertEquals("Test Student", settings.signedInAs.first())
        assertFalse(settings.signInExpired.first())
        assertTrue(backgroundOn)
    }

    @Test
    fun notSignedInYet_savesNothing() = runBlocking {
        account.setAddress("canvas.example.edu")
        whoAmIResult = { throw CanvasException.NotSignedIn() }
        assertEquals(SignInResult.NotYet, account.confirmSignIn())
        assertNull(settings.signedInAs.first())
        assertFalse(backgroundOn)
    }

    @Test
    fun offline_isReportedAsAFailure() = runBlocking {
        account.setAddress("canvas.example.edu")
        whoAmIResult = { throw CanvasException.Offline(java.io.IOException("no network")) }
        assertTrue(account.confirmSignIn() is SignInResult.Failed)
        assertNull(settings.signedInAs.first())
    }

    @Test
    fun noAddress_cantSignIn() = runBlocking {
        assertTrue(account.confirmSignIn() is SignInResult.Failed)
    }

    @Test
    fun signOut_forgetsTheBrowserAndName_butKeepsTheAddress() = runBlocking {
        account.setAddress("canvas.example.edu")
        account.confirmSignIn()
        account.signOut()
        assertNull(settings.signedInAs.first())
        assertEquals(1, browserCleared)
        assertFalse(backgroundOn)
        assertEquals("https://canvas.example.edu", settings.address.first())
    }

    @Test
    fun changingToAnotherSchool_signsOutFirst() = runBlocking {
        account.setAddress("canvas.example.edu")
        account.confirmSignIn()
        account.setAddress("canvas.other.edu")
        assertNull(settings.signedInAs.first())
        assertEquals(1, browserCleared)
        // Setting the same address again doesn't sign out.
        account.confirmSignIn()
        account.setAddress("https://canvas.other.edu")
        assertEquals("Test Student", settings.signedInAs.first())
    }
}
