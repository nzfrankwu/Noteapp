package com.nzfrankwu.noteapp.ui.notebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the lasso copied or cut, kept while the app runs so it can be pasted into ANY notebook
 * (PHASE-5.md 5.10): the other notebook in split view, or one opened later. [Content.notebookId]
 * is where it came from, so pictures can be copied into the notebook they're pasted in.
 */
class EditorClipboard {
    data class Content(val notebookId: String, val clip: NotebookViewModel.Clip)

    var content by mutableStateOf<Content?>(null)
}
