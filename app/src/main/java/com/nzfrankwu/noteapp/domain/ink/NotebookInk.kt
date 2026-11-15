package com.nzfrankwu.noteapp.domain.ink

/** One change to a notebook, as kept for undo and redo. */
sealed interface InkEdit {
    /** Strokes drawn on a page (added on top). */
    data class Add(val pageId: String, val strokes: List<InkStroke>) : InkEdit

    /** Strokes erased from a page, with where each one was (so undo puts it back in order). */
    data class Remove(val pageId: String, val removed: List<IndexedValue<InkStroke>>) : InkEdit

    /** Strokes changed in place (lasso move or resize): [before] and [after] have the same IDs. */
    data class Replace(val pageId: String, val before: List<InkStroke>, val after: List<InkStroke>) : InkEdit

    /** A page added at [index]. Undo deletes it again (kept, hidden). */
    data class AddPage(val pageId: String, val index: Int) : InkEdit

    /** A page deleted from [index] (kept, hidden). Undo brings it back there. */
    data class DeletePage(val pageId: String, val index: Int) : InkEdit
}

/**
 * The strokes of an open notebook, page by page, with undo and redo (PHASE-2.md 2.8).
 * Plain Kotlin: the editor screen shows it and saves the pages it reports as changed.
 *
 * Page additions and deletions are recorded here for undo, but carried out by the caller (they
 * change the database): [undo] and [redo] return the edit so the caller can do the page part.
 */
class NotebookInk(private val maxUndo: Int = 200) {
    private val pages = HashMap<String, MutableList<InkStroke>>()
    private val undoStack = ArrayDeque<InkEdit>()
    private val redoStack = ArrayDeque<InkEdit>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun strokes(pageId: String): List<InkStroke> = pages[pageId] ?: emptyList()

    fun isLoaded(pageId: String): Boolean = pageId in pages

    /** Puts a page's saved strokes in (when the notebook opens). Not an undoable edit. */
    fun load(pageId: String, strokes: List<InkStroke>) {
        pages[pageId] = strokes.toMutableList()
    }

    /** Draws [strokes] on top of the page. */
    fun add(pageId: String, strokes: List<InkStroke>) {
        if (strokes.isEmpty()) return
        record(InkEdit.Add(pageId, strokes))
    }

    /** Erases the strokes with these IDs. Returns how many were erased. */
    fun erase(pageId: String, ids: Set<String>): Int {
        val list = pages[pageId] ?: return 0
        val removed = list.withIndex().filter { it.value.id in ids }
        if (removed.isEmpty()) return 0
        record(InkEdit.Remove(pageId, removed))
        return removed.size
    }

    /** Replaces strokes in place (same IDs, e.g. after moving them with the lasso). */
    fun replace(pageId: String, after: List<InkStroke>) {
        val list = pages[pageId] ?: return
        val byId = after.associateBy { it.id }
        val before = list.filter { it.id in byId }
        if (before.isEmpty()) return
        record(InkEdit.Replace(pageId, before, before.map { byId.getValue(it.id) }))
    }

    /** Records a page added or deleted by the caller, so it can be undone. */
    fun recordPageEdit(edit: InkEdit) {
        require(edit is InkEdit.AddPage || edit is InkEdit.DeletePage)
        undoStack.addLast(edit)
        trim()
        redoStack.clear()
    }

    /** Undoes the last edit and returns it (the caller saves its page, or restores/removes a page). */
    fun undo(): InkEdit? {
        val edit = undoStack.removeLastOrNull() ?: return null
        revert(edit)
        redoStack.addLast(edit)
        return edit
    }

    /** Redoes the last undone edit and returns it. */
    fun redo(): InkEdit? {
        val edit = redoStack.removeLastOrNull() ?: return null
        apply(edit)
        undoStack.addLast(edit)
        return edit
    }

    private fun record(edit: InkEdit) {
        apply(edit)
        undoStack.addLast(edit)
        trim()
        redoStack.clear()
    }

    private fun trim() {
        while (undoStack.size > maxUndo) undoStack.removeFirst()
    }

    private fun page(pageId: String) = pages.getOrPut(pageId) { mutableListOf() }

    private fun apply(edit: InkEdit) {
        when (edit) {
            is InkEdit.Add -> page(edit.pageId).addAll(edit.strokes)
            is InkEdit.Remove -> {
                val ids = edit.removed.map { it.value.id }.toSet()
                page(edit.pageId).removeAll { it.id in ids }
            }
            is InkEdit.Replace -> swap(edit.pageId, edit.after)
            is InkEdit.AddPage, is InkEdit.DeletePage -> Unit
        }
    }

    private fun revert(edit: InkEdit) {
        when (edit) {
            is InkEdit.Add -> {
                val ids = edit.strokes.map { it.id }.toSet()
                page(edit.pageId).removeAll { it.id in ids }
            }
            is InkEdit.Remove -> {
                val list = page(edit.pageId)
                for ((index, stroke) in edit.removed.sortedBy { it.index }) list.add(index.coerceAtMost(list.size), stroke)
            }
            is InkEdit.Replace -> swap(edit.pageId, edit.before)
            is InkEdit.AddPage, is InkEdit.DeletePage -> Unit
        }
    }

    private fun swap(pageId: String, replacements: List<InkStroke>) {
        val byId = replacements.associateBy { it.id }
        val list = page(pageId)
        for (i in list.indices) byId[list[i].id]?.let { list[i] = it }
    }
}
