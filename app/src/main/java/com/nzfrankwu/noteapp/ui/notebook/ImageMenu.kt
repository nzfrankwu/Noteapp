package com.nzfrankwu.noteapp.ui.notebook

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.data.PhotoAccess
import com.nzfrankwu.noteapp.data.RecentImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The image menu (PHASE-3.md 3.5, like Notein's, `design-reference/insert image menu.jpg`): a
 * panel under the Image button with
 *  - a header: ✓ (pick several), "Image", ✕
 *  - Recent Images: the newest photos and screenshots, 3 across; tap one to insert it (hold one
 *    to insert it as a new page instead)
 *  - Photo Library (Android's photo picker) and Take Photo (the camera app).
 *
 * [onInsert] gets the chosen pictures, and whether each should become a page of its own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageMenu(
    onInsert: (uris: List<Uri>, asNewPage: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var access by remember { mutableStateOf(RecentImages.access(context)) }
    var asked by remember { mutableStateOf(false) }
    // Coming back from Settings (or the "Select photos" sheet) may have changed what's allowed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = RecentImages.access(context) }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        access = RecentImages.access(context)
    }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) onInsert(uris, false)
    }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null && file.length() > 0) onInsert(listOf(Uri.fromFile(file)), false)
    }

    var picking by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Uri>() }

    // The grid's photos, loaded a page at a time as it scrolls.
    val photos = remember { mutableStateListOf<Uri>() }
    var endReached by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= photos.size - 9 }
    }
    LaunchedEffect(access, nearEnd) {
        if (access == PhotoAccess.NONE) { photos.clear(); endReached = false; return@LaunchedEffect }
        if (!nearEnd || endReached) return@LaunchedEffect
        val more = withContext(Dispatchers.IO) { RecentImages.page(resolver, photos.size, PAGE_SIZE) }
        photos.addAll(more.filter { it !in photos })
        if (more.size < PAGE_SIZE) endReached = true
    }
    LaunchedEffect(access) {
        // Reload from the top when what's allowed changes.
        photos.clear()
        endReached = false
    }

    Surface(
        modifier = modifier.width(PanelWidth).testTag("image-menu"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            // ---- Header ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { picking = !picking; picked.clear() },
                    modifier = Modifier.testTag("image-pick-several"),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check_circle),
                        contentDescription = if (picking) "Stop picking several" else "Pick several",
                        tint = if (picking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (picking) "${picked.size} picked" else "Image",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                }
            }
            HorizontalDivider()
            Text("Recent Images", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))

            // ---- Recent Images ----
            Box(Modifier.fillMaxWidth().height(GridHeight)) {
                when {
                    access == PhotoAccess.NONE -> AllowAccess(
                        afterRefusal = asked,
                        onAllow = { askPermission.launch(RecentImages.permissions) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                    )
                    photos.isEmpty() && endReached -> Text(
                        "No photos yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().testTag("recent-images"),
                    ) {
                        items(photos, key = { it.toString() }) { uri ->
                            var menuOpen by remember { mutableStateOf(false) }
                            val isPicked = uri in picked
                            Box {
                                Thumbnail(
                                    uri = uri,
                                    picked = isPicked,
                                    modifier = Modifier.combinedClickable(
                                        onClick = {
                                            if (picking) {
                                                if (isPicked) picked.remove(uri) else picked.add(uri)
                                            } else {
                                                onInsert(listOf(uri), false)
                                            }
                                        },
                                        onLongClick = { if (!picking) menuOpen = true },
                                    ),
                                )
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Insert on this page") }, onClick = { menuOpen = false; onInsert(listOf(uri), false) })
                                    DropdownMenuItem(text = { Text("Insert as a new page") }, onClick = { menuOpen = false; onInsert(listOf(uri), true) })
                                }
                            }
                        }
                    }
                }
            }
            if (access == PhotoAccess.SELECTED) {
                TextButton(onClick = { askPermission.launch(RecentImages.permissions) }) { Text("Choose more photos to show") }
            }
            if (picking) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { picking = false; picked.clear() }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = picked.isNotEmpty(),
                        onClick = { onInsert(picked.toList(), false); picking = false; picked.clear() },
                        modifier = Modifier.testTag("image-insert-picked"),
                    ) { Text(if (picked.size > 1) "Insert ${picked.size}" else "Insert") }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // ---- Photo Library, Take Photo ----
            MenuRow(R.drawable.ic_photo_library, "Photo Library") {
                pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            HorizontalDivider()
            MenuRow(R.drawable.ic_photo_camera, "Take Photo") {
                val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
                val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
                cameraFile = file
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                runCatching { takePhoto.launch(uri) }
            }
        }
    }
}

@Composable
private fun AllowAccess(afterRefusal: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (afterRefusal) "Noteapp can't see your photos. You can allow it in Settings, or use Photo Library below."
            else "Let Noteapp show your recent photos and screenshots here.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (afterRefusal) {
            TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("photos-open-settings")) { Text("Open Settings") }
        } else {
            TextButton(onClick = onAllow, modifier = Modifier.testTag("photos-allow")) { Text("Allow access") }
        }
    }
}

@Composable
private fun Thumbnail(uri: Uri, picked: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 120.dp.roundToPx() }
    val image by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { RecentImages.thumbnail(context.contentResolver, uri, sizePx)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (picked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .semantics {
                contentDescription = "Recent photo"
                selected = picked
            },
    ) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (picked) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private val PanelWidth = 360.dp
private val GridHeight = 330.dp
private const val PAGE_SIZE = 60
private const val MAX_PICK = 20

/** Photos taken with Take Photo wait here (cache) until they're copied into the notebook. */
const val CAMERA_DIR = "camera"
