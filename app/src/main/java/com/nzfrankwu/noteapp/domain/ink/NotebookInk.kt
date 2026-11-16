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

    /** Pictures placed on a page (on top of the page's other pictures, under all ink). */
    data class AddImages(val pageId: String, val images: List<InkImage>) : InkEdit

    /** Pictures taken off a page, with where each one was in the page's order. */
    data class RemoveImages(val pageId: String, val removed: List<IndexedValue<InkImage>>) : InkEdit

    /** Pictures moved, resized or turned: [before] and [after] have the same IDs. */
    data class ReplaceImages(val pageId: String, val before: List<InkImage>, val after: List<InkImage>) : InkEdit

    /** Several edits that undo and redo as one step (e.g. moving strokes and pictures together). */
    data class Group(val edits: List<InkEdit>) : InkEdit
}

/** The pages an edit changes the content of (strokes or pictures). */
val InkEdit.changedPages: Set<String>
    get() = when (this) {
        is InkEdit.Add -> setOf(pageId)
        is InkEdit.Remove -> setOf(pageId)
        is InkEdit.Replace -> setOf(pageId)
        is InkEdit.AddImages -> setOf(pageId)
        is InkEdit.RemoveImages -> setOf(pageId)
        is InkEdit.ReplaceImages -> setOf(pageId)
        is InkEdit.Group -> edits.flatMapTo(HashSet()) { it.changedPages }
        is InkEdit.AddPage, is InkEdit.DeletePage -> emptySet()
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
    private val pictures = HashMap<String, MutableList<InkImage>>()
    private val undoStack = ArrayDeque<InkEdit>()
    private val redoStack = ArrayDeque<InkEdit>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun strokes(pageId: String): List<InkStroke> = pages[pageId] ?: emptyList()

    /** The page's pictures, bottom first. */
    fun images(pageId: String): List<InkImage> = pictures[pageId] ?: emptyList()

    fun isLoaded(pageId: String): Boolean = pageId in pages

    /** Puts a page's saved strokes and pictures in (when the notebook opens). Not an undoable edit. */
    fun load(pageId: String, strokes: List<InkStroke>, images: List<InkImage> = emptyList()) {
        pages[pageId] = strokes.toMutableList()
        pictures[pageId] = images.toMutableList()
    }

    /** Places [images] on the page, on top of its other pictures. */
    fun addImages(pageId: String, images: List<InkImage>) {
        if (images.isEmpty()) return
        record(InkEdit.AddImages(pageId, images))
    }

    /**
     * Takes away the strokes with [strokeIds] and the pictures with [imageIds] as ONE undo step
     * (the lasso's Delete and Cut). Returns how many things were taken away.
     */
    fun remove(pageId: String, strokeIds: Set<String>, imageIds: Set<String>): Int {
        val strokes = pages[pageId]?.withIndex()?.filter { it.value.id in strokeIds }.orEmpty()
        val images = pictures[pageId]?.withIndex()?.filter { it.value.id in imageIds }.orEmpty()
        val edits = buildList {
            if (strokes.isNotEmpty()) add(InkEdit.Remove(pageId, strokes))
            if (images.isNotEmpty()) add(InkEdit.RemoveImages(pageId, images))
        }
        recordAll(edits)
        return strokes.size + images.size
    }

    /**
     * Replaces strokes and pictures in place (same IDs) as ONE undo step: the lasso moving,
     * resizing or turning a selection of both.
     */
    fun replace(pageId: String, strokes: List<InkStroke>, images: List<InkImage>) {
        val edits = buildList {
            val list = pages[pageId].orEmpty()
            val byId = strokes.associateBy { it.id }
            val before = list.filter { it.id in byId }
            if (before.isNotEmpty()) add(InkEdit.Replace(pageId, before, before.map { byId.getValue(it.id) }))
            val imgs = pictures[pageId].orEmpty()
            val imgById = images.associateBy { it.id }
            val imgBefore = imgs.filter { it.id in imgById }
            if (imgBefore.isNotEmpty()) add(InkEdit.ReplaceImages(pageId, imgBefore, imgBefore.map { imgById.getValue(it.id) }))
        }
        recordAll(edits)
    }

    /** Draws strokes on several pages as ONE undo step (a stroke split at a page edge, F36). */
    fun addAcross(byPage: Map<String, List<InkStroke>>) {
        recordAll(byPage.filterValues { it.isNotEmpty() }.map { (pageId, strokes) -> InkEdit.Add(pageId, strokes) })
    }

    /** Adds strokes and pictures as ONE undo step (the lasso's Paste and Duplicate). */
    fun add(pageId: String, strokes: List<InkStroke>, images: List<InkImage>) {
        recordAll(
            buildList {
                if (strokes.isNotEmpty()) add(InkEdit.Add(pageId, strokes))
                if (images.isNotEmpty()) add(InkEdit.AddImages(pageId, images))
            },
        )
    }

    private fun recordAll(edits: List<InkEdit>) {
        when (edits.size) {
            0 -> Unit
            1 -> record(edits.single())
            else -> record(InkEdit.Group(edits))
        }
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
    private fun picturesOf(pageId: String) = pictures.getOrPut(pageId) { mutableListOf() }

    private fun apply(edit: InkEdit) {
        when (edit) {
            is InkEdit.Add -> page(edit.pageId).addAll(edit.strokes)
            is InkEdit.Remove -> {
                val ids = edit.removed.map { it.value.id }.toSet()
                page(edit.pageId).removeAll { it.id in ids }
            }
            is InkEdit.Replace -> swap(edit.pageId, edit.after)
            is InkEdit.AddImages -> picturesOf(edit.pageId).addAll(edit.images)
            is InkEdit.RemoveImages -> {
                val ids = edit.removed.map { it.value.id }.toSet()
                picturesOf(edit.pageId).removeAll { it.id in ids }
            }
            is InkEdit.ReplaceImages -> swapImages(edit.pageId, edit.after)
            is InkEdit.Group -> edit.edits.forEach { apply(it) }
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
            is InkEdit.AddImages -> {
                val ids = edit.images.map { it.id }.toSet()
                picturesOf(edit.pageId).removeAll { it.id in ids }
            }
            is InkEdit.RemoveImages -> {
                val list = picturesOf(edit.pageId)
                for ((index, image) in edit.removed.sortedBy { it.index }) list.add(index.coerceAtMost(list.size), image)
            }
            is InkEdit.ReplaceImages -> swapImages(edit.pageId, edit.before)
            is InkEdit.Group -> edit.edits.asReversed().forEach { revert(it) }
            is InkEdit.AddPage, is InkEdit.DeletePage -> Unit
        }
    }

    private fun swapImages(pageId: String, replacements: List<InkImage>) {
        val byId = replacements.associateBy { it.id }
        val list = picturesOf(pageId)
        for (i in list.indices) byId[list[i].id]?.let { list[i] = it }
    }

    private fun swap(pageId: String, replacements: List<InkStroke>) {
        val byId = replacements.associateBy { it.id }
        val list = page(pageId)
        for (i in list.indices) byId[list[i].id]?.let { list[i] = it }
    }
}
