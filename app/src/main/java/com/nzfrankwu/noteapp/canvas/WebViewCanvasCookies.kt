package com.nzfrankwu.noteapp.canvas

import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The Canvas sign-in cookies, read from the in-app browser's own cookie store (in Noteapp's
 * private storage). They're read fresh for each request and never copied, stored elsewhere,
 * logged or backed up (IDEA.md §3.5).
 */
class WebViewCanvasCookies : CanvasCookieSource {
    override fun cookieHeaderFor(url: String): String? =
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }

    /** "Sign out of Canvas": forgets every cookie and all web storage of the in-app browser. */
    suspend fun signOut() = withContext(Dispatchers.Main) {
        val cookies = CookieManager.getInstance()
        suspendCancellableCoroutine { done -> cookies.removeAllCookies { done.resume(Unit) } }
        cookies.flush()
        WebStorage.getInstance().deleteAllData()
    }
}
