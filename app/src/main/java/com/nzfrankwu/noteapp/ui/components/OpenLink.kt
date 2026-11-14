package com.nzfrankwu.noteapp.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
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
