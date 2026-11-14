package com.nzfrankwu.noteapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.ui.components.EmptyState
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.theme.NoteAppTheme

/**
 * Below this width the home screen stacks Due soon above Folders (split-screen, small tablets);
 * at or above it they sit side by side. The OPPO Pad 3 is 800 dp wide in portrait and 1120 dp in
 * landscape, so it gets side by side in both orientations.
 */
val WideLayoutMinWidth = 720.dp

/** Due soon's share of the width in the side-by-side layout. */
private const val DueSoonWidthFraction = 0.4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("NoteApp") },
                actions = {
                    RoundIconButton(
                        icon = R.drawable.ic_settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(Modifier.padding(innerPadding).fillMaxSize()) {
            if (maxWidth >= WideLayoutMinWidth) WideHome() else NarrowHome()
        }
    }
}

/** Side by side: Due soon panel on the left, folders on the right. */
@Composable
private fun WideHome() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .testTag("home-wide"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DueSoonPanel(
            fillHeight = true,
            modifier = Modifier
                .weight(DueSoonWidthFraction)
                .fillMaxHeight(),
        )
        FoldersArea(
            fillHeight = true,
            modifier = Modifier
                .weight(1f - DueSoonWidthFraction)
                .fillMaxHeight(),
        )
    }
}

/** Stacked: Due soon on top, folders below, scrolling together. */
@Composable
private fun NarrowHome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .testTag("home-narrow"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DueSoonPanel(fillHeight = false, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp))
        FoldersArea(fillHeight = false, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp))
    }
}

/**
 * The "Due soon" list (empty until tasks arrive in steps 2.1 and 3.1).
 * [fillHeight]: true when the panel has a fixed height (side by side), so the empty message can
 * be centred in it; false in the scrolling stacked layout, where there's no height to fill.
 */
@Composable
private fun DueSoonPanel(fillHeight: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("Due soon")
            EmptyState(
                title = "Nothing due yet",
                message = "Tasks you add, and assignments from Canvas, will show up here.",
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.weight(1f) else Modifier),
            )
        }
    }
}

/** The folder grid (empty until folders arrive in step 1.1). */
@Composable
private fun FoldersArea(fillHeight: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 16.dp)) {
        SectionTitle("Folders", Modifier.padding(horizontal = 4.dp))
        EmptyState(
            title = "No folders yet",
            message = "Folders for your courses will show up here.",
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier),
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Preview(widthDp = 1120, heightDp = 800)
@Composable
private fun HomeWidePreview() {
    NoteAppTheme { HomeScreen(onOpenSettings = {}) }
}

@Preview(widthDp = 560, heightDp = 800)
@Composable
private fun HomeNarrowPreview() {
    NoteAppTheme { HomeScreen(onOpenSettings = {}) }
}
