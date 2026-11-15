package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzfrankwu.noteapp.R
import com.nzfrankwu.noteapp.ui.components.RoundIconButton
import com.nzfrankwu.noteapp.ui.components.drawPaperPattern
import com.nzfrankwu.noteapp.ui.theme.DeskDark
import com.nzfrankwu.noteapp.ui.theme.DeskLight
import com.nzfrankwu.noteapp.ui.theme.PaperLine
import com.nzfrankwu.noteapp.ui.theme.PaperWhite

/** An open notebook: its pages side by side on a "desk". (Writing arrives in steps 2.4–2.7.) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(viewModel: NotebookViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val notebook by viewModel.notebook.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(notebook?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { RoundIconButton(R.drawable.ic_arrow_back, "Back", onBack, Modifier.padding(start = 8.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        val nb = notebook ?: return@Scaffold
        LazyRow(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
                .background(if (isSystemInDarkTheme()) DeskDark else DeskLight)
                .testTag("notebook-pages"),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(pages, key = { it.id }) { page ->
                Box(
                    Modifier.fillMaxHeight().aspectRatio(nb.pageWidth / nb.pageHeight)
                        .shadow(4.dp).background(PaperWhite),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawPaperPattern(page.template ?: nb.template, nb.pageWidth, nb.pageHeight, PaperLine)
                    }
                }
            }
        }
    }
}
