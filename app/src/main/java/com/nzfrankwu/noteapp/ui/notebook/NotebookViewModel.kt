package com.nzfrankwu.noteapp.ui.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzfrankwu.noteapp.data.NotebookRepository
import com.nzfrankwu.noteapp.data.db.Notebook
import com.nzfrankwu.noteapp.data.db.Page
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** An open notebook: the notebook and its pages, in order. */
class NotebookViewModel(
    private val notebookId: String,
    private val notebooks: NotebookRepository,
) : ViewModel() {
    val notebook: StateFlow<Notebook?> = notebooks.observeNotebook(notebookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pages: StateFlow<List<Page>> = notebooks.observePages(notebookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
