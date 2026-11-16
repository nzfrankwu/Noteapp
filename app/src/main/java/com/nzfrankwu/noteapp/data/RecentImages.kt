package com.nzfrankwu.noteapp.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import kotlin.math.max

/** How much of the tablet's photos Noteapp may show. */
enum class PhotoAccess {
    /** Every photo. */
    ALL,
    /** Only the photos the person picked (Android 14's "Select photos"). */
    SELECTED,
    NONE,
}

/**
 * The newest photos and screenshots on the tablet, for the image menu's Recent Images grid
 * (PHASE-3.md 3.5). Read-only: Noteapp never changes or deletes them.
 */
object RecentImages {
    /** The permissions to ask for, depending on the Android version. */
    val permissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun access(context: Context): PhotoAccess {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccess.ALL
            Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.SELECTED
            Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> PhotoAccess.ALL
            else -> PhotoAccess.NONE
        }
    }

    /** Up to [limit] photos, newest first, skipping the first [offset]. Blocking. */
    fun page(resolver: ContentResolver, offset: Int, limit: Int): List<Uri> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val args = Bundle().apply {
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_ADDED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        return try {
            val cursor = if (Build.VERSION.SDK_INT >= 30) {
                resolver.query(collection, arrayOf(MediaStore.Images.Media._ID), args, null)
            } else {
                // Before Android 11 the gallery ignores the paging arguments; ask in its sort order instead.
                resolver.query(
                    collection, arrayOf(MediaStore.Images.Media._ID), null, null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit OFFSET $offset",
                )
            }
            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                buildList { while (c.moveToNext()) add(ContentUris.withAppendedId(collection, c.getLong(idCol))) }
            }.orEmpty()
        } catch (e: Exception) {
            emptyList() // no permission (any more), or the gallery can't be read
        }
    }

    /** A small picture of the photo for the grid. Blocking; null if it can't be read. */
    fun thumbnail(resolver: ContentResolver, uri: Uri, sizePx: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }
    } catch (e: Exception) {
        null
    }
}
