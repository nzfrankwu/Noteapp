package com.nzfrankwu.noteapp.canvas

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nzfrankwu.noteapp.data.CanvasSettings
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The in-app browser's cookie store, and the Canvas settings store, on a real device. */
@RunWith(AndroidJUnit4::class)
class WebViewCanvasCookiesTest {
    private val address = "https://canvas.example.edu"

    @Test
    fun cookiesSetByTheBrowser_areRead_andSignOutClearsThem() = runBlocking {
        val cookies = WebViewCanvasCookies()
        withContext(Dispatchers.Main) {
            CookieManager.getInstance().setCookie(address, "made_up_session=test-value; Secure")
            CookieManager.getInstance().flush()
        }
        assertEquals("made_up_session=test-value", cookies.cookieHeaderFor(address))

        cookies.signOut()

        assertNull(cookies.cookieHeaderFor(address))
    }

    @Test
    fun canvasSettings_rememberAddressAndName() = runBlocking {
        val settings = CanvasSettings(InstrumentationRegistry.getInstrumentation().targetContext)
        settings.setAddress(address)
        settings.setSignedInAs("Test Student")
        assertEquals(address, settings.address.first())
        assertEquals("Test Student", settings.signedInAs.first())

        settings.setAddress(null)
        settings.setSignedInAs(null)
        assertNull(settings.address.first())
        assertNull(settings.signedInAs.first())
    }
}
