package com.nzfrankwu.noteapp.ui.notebook

import android.view.KeyEvent
import com.nzfrankwu.noteapp.domain.ink.PenButtonKeys

/**
 * Pen side-button clicks that arrive as keys (PHASE-2.md F2): the OPPO Pencil 2 sends its button
 * over Bluetooth as `F21`, which reaches the activity, not the notebook's touch handling.
 * [MainActivity] passes every key here; an open notebook sets [onClick] while it's on screen.
 * Main thread only.
 */
object PenButtonKeyEvents {
    /** What a click does right now, or null when no notebook is open. */
    var onClick: (() -> Unit)? = null

    /** Returns true when the key was a pen button handled by an open notebook. */
    fun handle(event: KeyEvent): Boolean {
        val listener = onClick ?: return false
        if (!PenButtonKeys.isPenButton(event.keyCode, event.scanCode)) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) listener()
        return true // the matching key-up is ours too
    }
}
