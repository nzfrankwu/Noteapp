package com.nzfrankwu.noteapp.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nzfrankwu.noteapp.data.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where notebook tile pictures come from (null in screen tests: tiles then show the paper pattern). */
val LocalThumbnails = staticCompositionLocalOf<Thumbnails?> { null }

/**
 * A notebook's tile picture, loaded off the main thread; null while loading or if there's none
 * yet. [version] (the notebook's last change) makes it load again after an edit.
 */
@Composable
fun rememberThumbnail(thumbnails: Thumbnails?, notebookId: String, version: Long): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, thumbnails, notebookId, version) {
        if (thumbnails == null) return@produceState
        value = withContext(Dispatchers.IO) {
            val file = thumbnails.file(notebookId)
            if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() else null
        } ?: value
    }
