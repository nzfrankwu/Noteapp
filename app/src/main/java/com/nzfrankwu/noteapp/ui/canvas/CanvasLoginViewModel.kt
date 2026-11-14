package com.nzfrankwu.noteapp.ui.canvas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.canvas.CanvasAccount
import com.nzfrankwu.noteapp.canvas.CanvasSignIn
import com.nzfrankwu.noteapp.canvas.SignInResult
import com.nzfrankwu.noteapp.data.CanvasSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The in-app browser sign-in's state. */
sealed interface CanvasLoginState {
    data object Loading : CanvasLoginState
    /** No address saved: nothing to show (shouldn't happen; Settings saves it first). */
    data object NoAddress : CanvasLoginState
    /** Showing the browser at [address]; [checking] while asking Canvas "who am I?". */
    data class Browsing(val address: String, val checking: Boolean = false, val error: String? = null) : CanvasLoginState
    data class SignedIn(val name: String) : CanvasLoginState
}

/**
 * Signing in to Canvas in the in-app browser (IDEA.md §3.1). You log in on the real Canvas /
 * university pages; whenever a page finishes loading on the Canvas site outside its login pages,
 * Noteapp asks Canvas who is signed in. Once Canvas answers with a name, the sign-in is done.
 */
class CanvasLoginViewModel(
    settings: CanvasSettings,
    private val account: CanvasAccount,
) : ViewModel() {
    private val _state = MutableStateFlow<CanvasLoginState>(CanvasLoginState.Loading)
    val state: StateFlow<CanvasLoginState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val address = settings.address.first()
            _state.value = if (address == null) CanvasLoginState.NoAddress else CanvasLoginState.Browsing(address)
        }
    }

    /** Called by the browser after each page load. The URL is only checked, never stored or logged. */
    fun onPageFinished(url: String?) {
        val current = _state.value as? CanvasLoginState.Browsing ?: return
        if (current.checking || !CanvasSignIn.looksSignedIn(url, current.address)) return
        _state.value = current.copy(checking = true, error = null)
        viewModelScope.launch {
            _state.value = when (val result = account.confirmSignIn()) {
                is SignInResult.SignedIn -> CanvasLoginState.SignedIn(result.name)
                SignInResult.NotYet -> current.copy(checking = false)
                is SignInResult.Failed -> current.copy(checking = false, error = result.message)
            }
        }
    }

    /** The browser refused a page (e.g. a plain http:// or non-web link). */
    fun onBlockedPage() {
        val current = _state.value as? CanvasLoginState.Browsing ?: return
        _state.value = current.copy(error = "Noteapp only opens secure (https) pages while signing in, so a page was skipped.")
    }
}
