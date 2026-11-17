package com.nzfrankwu.noteapp.data

/**
 * Which notebooks are open in the editor right now (either pane of split view). Their Undo can
 * bring back pictures that are no longer on a page, so the clean-up of unused files leaves them alone.
 */
object OpenNotebooks {
    private val counts = HashMap<String, Int>()

    @Synchronized
    fun opened(notebookId: String) {
        counts[notebookId] = (counts[notebookId] ?: 0) + 1
    }

    @Synchronized
    fun closed(notebookId: String) {
        val n = (counts[notebookId] ?: return) - 1
        if (n <= 0) counts.remove(notebookId) else counts[notebookId] = n
    }

    @Synchronized
    fun ids(): Set<String> = counts.keys.toSet()
}
