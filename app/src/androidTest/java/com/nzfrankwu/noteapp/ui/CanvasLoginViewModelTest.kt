package com.nzfrankwu.noteapp.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzfrankwu.noteapp.canvas.CanvasAccount
import com.nzfrankwu.noteapp.canvas.CanvasException
import com.nzfrankwu.noteapp.canvas.CanvasUser
import com.nzfrankwu.noteapp.data.CanvasSettings
import com.nzfrankwu.noteapp.ui.canvas.CanvasLoginState
import com.nzfrankwu.noteapp.ui.canvas.CanvasLoginViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** When the in-app browser's pages count as "signed in" (no real Canvas or browser needed). */
@RunWith(AndroidJUnit4::class)
class CanvasLoginViewModelTest {
    private val settings = CanvasSettings(InstrumentationRegistry.getInstrumentation().targetContext)
    private var whoAmICalls = 0
    private var signedIn = true
    private val account = CanvasAccount(
        settings = settings,
        whoAmI = {
            whoAmICalls++
            if (signedIn) CanvasUser(7, "Test Student") else throw CanvasException.NotSignedIn()
        },
        clearBrowser = {},
        startBackgroundSync = {},
        stopBackgroundSync = {},
    )

    @Before
    fun setUp() = runBlocking {
        settings.setSignedInAs(null)
        settings.setAddress("https://canvas.example.edu")
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        settings.setAddress(null)
        settings.setSignedInAs(null)
        Unit
    }

    /** Creates the ViewModel on the main thread (as the app does) and waits for it to load. */
    private fun viewModel(): CanvasLoginViewModel {
        lateinit var vm: CanvasLoginViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm = CanvasLoginViewModel(settings, account) }
        runBlocking { withTimeout(5_000) { vm.state.first { it is CanvasLoginState.Browsing } } }
        return vm
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test
    fun loginPages_andOtherSites_dontTriggerACheck() {
        val vm = viewModel()
        onMain {
            vm.onPageFinished("https://canvas.example.edu/login/saml")
            vm.onPageFinished("https://login.university.example.ac.nz/idp/profile/SAML2")
            vm.onPageFinished("https://canvas.example.edu.evil.example.com/")
        }
        assertEquals(0, whoAmICalls)
    }

    @Test
    fun canvasDashboard_confirmsTheSignIn() = runBlocking {
        val vm = viewModel()
        onMain { vm.onPageFinished("https://canvas.example.edu/") }
        val done = withTimeout(5_000) { vm.state.first { it is CanvasLoginState.SignedIn } }
        assertEquals(CanvasLoginState.SignedIn("Test Student"), done)
        assertEquals("Test Student", settings.signedInAs.first())
    }

    @Test
    fun canvasSaysNotSignedIn_keepsBrowsing() = runBlocking {
        signedIn = false
        val vm = viewModel()
        onMain { vm.onPageFinished("https://canvas.example.edu/") }
        withTimeout(5_000) { vm.state.first { it is CanvasLoginState.Browsing && !it.checking && whoAmICalls == 1 } }
        assertTrue(vm.state.value is CanvasLoginState.Browsing)
    }
}
