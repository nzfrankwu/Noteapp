package com.nzfrankwu.noteapp.ui.canvas

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.theme.Dimens

/**
 * "Sign in to Canvas": the school's real Canvas (and university login) pages in an in-app
 * browser. When Canvas confirms who is signed in, [onSignedIn] is called with the name.
 *
 * Safety (IDEA.md §3.5): only https pages are opened; the browser can't read files; no
 * JavaScript bridge into the app; page addresses are never logged. Cookies stay in the browser's
 * own private store, which is where the read-only Canvas client reads them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasLoginScreen(viewModel: CanvasLoginViewModel, onClose: () -> Unit, onSignedIn: (name: String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? CanvasLoginState.SignedIn)?.let { onSignedIn(it.name) } }
    val browsing = state as? CanvasLoginState.Browsing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sign in to Canvas")
                        browsing?.let {
                            Text(
                                it.address.removePrefix("https://"),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = { RoundIconButton(R.drawable.ic_close, "Cancel", onClose, Modifier.padding(start = 8.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (val s = state) {
                CanvasLoginState.Loading, is CanvasLoginState.SignedIn -> Unit
                CanvasLoginState.NoAddress -> Text(
                    "Type your school's Canvas address in Settings first.",
                    modifier = Modifier.padding(Dimens.ScreenPadding),
                )
                is CanvasLoginState.Browsing -> {
                    CanvasBrowser(s.address, viewModel::onPageFinished, viewModel::onBlockedPage, onClose)
                    if (s.checking || s.error != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().testTag("canvas-login-status"),
                        ) {
                            Column(Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp)) {
                                if (s.checking) {
                                    Text("Checking your sign-in with Canvas…")
                                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                                }
                                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled") // Canvas and university logins need JavaScript.
@Composable
private fun CanvasBrowser(address: String, onPageFinished: (String?) -> Unit, onBlocked: () -> Unit, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    // Back goes back a page in the browser first, then closes the screen.
    BackHandler { if (canGoBack) webView?.goBack() else onClose() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("canvas-browser"),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    CookieManager.getInstance().setAcceptCookie(true)
                    // University single sign-on often runs on another site inside the login flow.
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val secure = request.url.scheme.equals("https", ignoreCase = true)
                            if (!secure && request.isForMainFrame) onBlocked()
                            return !secure // true = don't open it
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            canGoBack = view.canGoBack()
                            CookieManager.getInstance().flush() // keep the sign-in across app restarts
                            onPageFinished(url)
                        }
                    }
                    loadUrl(address)
                    webView = this
                }
            },
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                destroy()
            }
        }
    }
}
