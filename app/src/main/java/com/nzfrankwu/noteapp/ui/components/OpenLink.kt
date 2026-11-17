package com.nzfrankwu.noteapp.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.nzfrankwu.noteapp.domain.Links

/**
 * Opens a web link in the tablet's browser. Only http/https links are opened (see
 * [Links.isOpenable]); anything else, or no browser, shows a short message instead of crashing.
 */
fun openLink(context: Context, url: String?) {
    if (!Links.isOpenable(url)) {
        Toast.makeText(context, "This link can't be opened.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url!!.trim().toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show()
    }
}

/** The Canvas Student app's package name. */
const val CANVAS_APP_PACKAGE = "com.instructure.candroid"

/**
 * "Open in Canvas" (PHASE-5.md FB11): the page in the Canvas Student app if it's installed and
 * can open it; otherwise in a Chrome tab over Noteapp (it uses Chrome's own sign-in, never
 * Noteapp's), and as a last resort in any browser.
 */
fun openInCanvas(context: Context, url: String?) {
    if (!Links.isOpenable(url)) {
        Toast.makeText(context, "This link can't be opened.", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = url!!.trim().toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(CANVAS_APP_PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    } catch (_: ActivityNotFoundException) {
        // No Canvas app, or it can't open this page: a Chrome tab.
    } catch (_: SecurityException) {
    }
    try {
        val tab = androidx.browser.customtabs.CustomTabsIntent.Builder().setShowTitle(true).build()
        // From Noteapp's own screen the tab opens over it, in Noteapp's task (Back returns here).
        // A new task only when there's no screen to open it over.
        if (!needsNewTask(context)) tab.launchUrl(context, uri)
        else tab.apply { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        openLink(context, url)
    }
}

/** Whether [context] has no activity behind it, so starting a screen from it needs a new task. */
internal fun needsNewTask(context: Context): Boolean {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) return false
        c = c.baseContext
    }
    return true
}
