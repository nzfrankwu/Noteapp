package com.nzfrankwu.noteapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.canvas.CanvasAccount
import com.nzfrankwu.noteapp.canvas.CanvasSyncRunner
import com.nzfrankwu.noteapp.canvas.FakeCanvas
import com.nzfrankwu.noteapp.canvas.SignInResult
import com.nzfrankwu.noteapp.canvas.SyncOutcome
import com.nzfrankwu.noteapp.data.CanvasSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

/** What Settings → Canvas shows. */
data class CanvasStatus(
    val loaded: Boolean = false,
    val address: String? = null,
    val signedInAs: String? = null,
    val lastSyncedAt: Instant? = null,
    val signInExpired: Boolean = false,
) {
    val isFake: Boolean get() = FakeCanvas.isFake(address)
}

/** Settings → Canvas: the address, sign-in, Sync now and Sign out. */
class CanvasSettingsViewModel(
    settings: CanvasSettings,
    private val account: CanvasAccount,
    private val runner: CanvasSyncRunner,
) : ViewModel() {

    val status: StateFlow<CanvasStatus> = combine(
        settings.address, settings.signedInAs, settings.lastSyncedAt, settings.signInExpired,
    ) { address, name, lastSynced, expired ->
        // "Last synced" starts as the epoch in tests and fresh installs; treat that as never.
        CanvasStatus(true, address, name, lastSynced?.takeIf { it.toEpochMilli() > 0 }, expired)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CanvasStatus())

    /** The address field, prefilled with the saved address (without https://). */
    private val _addressInput = MutableStateFlow("")
    val addressInput: StateFlow<String> = _addressInput.asStateFlow()

    private val _addressError = MutableStateFlow<String?>(null)
    val addressError: StateFlow<String?> = _addressError.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** A one-off message to show (e.g. the result of Sync now). Cleared with [messageShown]. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Set when the address is saved and the in-app browser should open. */
    private val _openBrowser = MutableStateFlow(false)
    val openBrowser: StateFlow<Boolean> = _openBrowser.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settings.address.first()
            if (_addressInput.value.isEmpty() && saved != null && !FakeCanvas.isFake(saved)) {
                _addressInput.value = saved.removePrefix("https://")
            }
        }
    }

    fun onAddressChange(text: String) {
        _addressInput.value = text
        _addressError.value = null
    }

    /** "Sign in": saves the address, then the screen opens the in-app browser. */
    fun startSignIn() {
        viewModelScope.launch {
            if (account.setAddress(_addressInput.value) == null) {
                _addressError.value = "Type your school's Canvas address, e.g. canvas.example.ac.nz (https only)."
            } else {
                _openBrowser.value = true
            }
        }
    }

    fun browserOpened() { _openBrowser.value = false }

    fun syncNow() {
        if (_syncing.value) return
        _syncing.value = true
        viewModelScope.launch {
            try {
                _message.value = SyncMessages.describe(runner.run())
            } finally {
                _syncing.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            account.signOut()
            _message.value = "Signed out of Canvas. Your courses and tasks are kept."
        }
    }

    /** Debug builds: connect to the made-up Canvas (no login needed) and sync it. */
    fun useFakeCanvas() {
        viewModelScope.launch {
            account.setAddress(FakeCanvas.ADDRESS)
            when (val result = account.confirmSignIn()) {
                is SignInResult.SignedIn -> syncNow()
                is SignInResult.Failed -> _message.value = result.message
                SignInResult.NotYet -> Unit
            }
        }
    }

    fun messageShown() { _message.value = null }
}

/** Plain-language results of a sync, for messages. */
object SyncMessages {
    fun describe(outcome: SyncOutcome): String = when (outcome) {
        is SyncOutcome.Synced -> {
            val s = outcome.summary
            val parts = buildList {
                add("${s.courses} course${if (s.courses == 1) "" else "s"}")
                if (s.tasksAdded > 0) add("${s.tasksAdded} new")
                if (s.tasksUpdated > 0) add("${s.tasksUpdated} updated")
                if (s.tasksMarkedRemoved > 0) add("${s.tasksMarkedRemoved} removed from Canvas")
            }
            val failed = if (s.failedCourses.isEmpty()) "" else " Couldn't read ${s.failedCourses.joinToString()}; will try again."
            "Synced with Canvas: ${parts.joinToString(", ")}.$failed"
        }
        SyncOutcome.NotSetUp -> "Canvas isn't connected. Sign in under Settings → Canvas."
        SyncOutcome.SignInExpired -> "Your Canvas sign-in expired. Sign in again to keep syncing. Your tasks are kept."
        is SyncOutcome.Failed -> "Couldn't sync with Canvas: ${outcome.message}."
    }
}
