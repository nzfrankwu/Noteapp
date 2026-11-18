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

    /** Text boxes placed on a page (on top of its other text boxes). */
    data class AddTexts(val pageId: String, val texts: List<InkText>) : InkEdit

    /** Text boxes taken off a page, with where each one was in the page's order. */
    data class RemoveTexts(val pageId: String, val removed: List<IndexedValue<InkText>>) : InkEdit

    /** Text boxes changed in place (typed in, moved, resized): [before] and [after] have the same IDs. */
    data class ReplaceTexts(val pageId: String, val before: List<InkText>, val after: List<InkText>) : InkEdit

    /** Several edits that undo and redo as one step (e.g. moving strokes and pictures together). */
    data class Group(val edits: List<InkEdit>) : InkEdit

    /**
     * The partial eraser cut [original] (at [index] in the page's drawing order) into [pieces]
     * (Phase 7, 7.4), which take its place in the drawing order. No pieces = it went completely.
     */
    data class Split(val pageId: String, val index: Int, val original: InkStroke, val pieces: List<InkStroke>) : InkEdit

    /**
     * One page's own paper changed (Phase 7, 7.3); null = the notebook's paper. Carried out by
     * the caller (it's in the database), like [AddPage].
     */
    data class PagePaper(
        val pageId: String,
        val before: com.nzfrankwu.noteapp.domain.PageTemplate?,
        val after: com.nzfrankwu.noteapp.domain.PageTemplate?,
    ) : InkEdit

    /**
     * The whole notebook's paper changed (Phase 7, 7.3): every page now uses [after]. [pagesBefore]
     * holds the pages that had their own paper, which this cleared. Carried out by the caller.
     */
    data class NotebookPaper(
        val before: com.nzfrankwu.noteapp.domain.PageTemplate,
        val after: com.nzfrankwu.noteapp.domain.PageTemplate,
        val pagesBefore: Map<String, com.nzfrankwu.noteapp.domain.PageTemplate>,
    ) : InkEdit
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
        is InkEdit.AddTexts -> setOf(pageId)
        is InkEdit.RemoveTexts -> setOf(pageId)
        is InkEdit.ReplaceTexts -> setOf(pageId)
        is InkEdit.Group -> edits.flatMapTo(HashSet()) { it.changedPages }
        is InkEdit.Split -> setOf(pageId)
        is InkEdit.AddPage, is InkEdit.DeletePage, is InkEdit.PagePaper, is InkEdit.NotebookPaper -> emptySet()
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
    private val textBoxes = HashMap<String, MutableList<InkText>>()
    private val undoStack = ArrayDeque<InkEdit>()
    private val redoStack = ArrayDeque<InkEdit>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun strokes(pageId: String): List<InkStroke> = pages[pageId] ?: emptyList()

    /** The page's pictures, bottom first. */
    fun images(pageId: String): List<InkImage> = pictures[pageId] ?: emptyList()

    /** The page's text boxes, bottom first. */
    fun texts(pageId: String): List<InkText> = textBoxes[pageId] ?: emptyList()

    /** Everything on the page, as it's saved (copies, safe to hand to another thread). */
    fun content(pageId: String): PageContent =
        PageContent(strokes(pageId).toList(), images(pageId).toList(), texts(pageId).toList())

    fun isLoaded(pageId: String): Boolean = pageId in pages

    /** Puts a page's saved strokes, pictures and text boxes in (when the notebook opens). Not an undoable edit. */
    fun load(pageId: String, strokes: List<InkStroke>, images: List<InkImage> = emptyList(), texts: List<InkText> = emptyList()) {
        pages[pageId] = strokes.toMutableList()
        pictures[pageId] = images.toMutableList()
        textBoxes[pageId] = texts.toMutableList()
    }

    /**
     * Finishes editing one text box as ONE undo step. [before] is the box as it was (null for a
     * new box) and [after] as it is now (null or blank text: the box is taken away). Returns the
     * edit recorded, or null when nothing changed.
     */
    fun commitText(pageId: String, before: InkText?, after: InkText?): InkEdit? {
        val kept = after?.takeIf { it.text.isNotBlank() }
        val edit: InkEdit? = when {
            before == null -> kept?.let { InkEdit.AddTexts(pageId, listOf(it)) }
            kept == null -> textsOf(pageId).withIndex().filter { it.value.id == before.id }
                .takeIf { it.isNotEmpty() }?.let { InkEdit.RemoveTexts(pageId, it) }
            else -> textsOf(pageId).firstOrNull { it.id == before.id }?.let { now ->
                val changed = kept.copy(id = now.id)
                if (changed == now) null else InkEdit.ReplaceTexts(pageId, listOf(now), listOf(changed))
            }
        }
        edit?.let { record(it) }
        return edit
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
    fun remove(pageId: String, strokeIds: Set<String>, imageIds: Set<String>, textIds: Set<String> = emptySet()): Int {
        val strokes = pages[pageId]?.withIndex()?.filter { it.value.id in strokeIds }.orEmpty()
        val images = pictures[pageId]?.withIndex()?.filter { it.value.id in imageIds }.orEmpty()
        val texts = textBoxes[pageId]?.withIndex()?.filter { it.value.id in textIds }.orEmpty()
        val edits = buildList {
            if (strokes.isNotEmpty()) add(InkEdit.Remove(pageId, strokes))
            if (images.isNotEmpty()) add(InkEdit.RemoveImages(pageId, images))
            if (texts.isNotEmpty()) add(InkEdit.RemoveTexts(pageId, texts))
        }
        recordAll(edits)
        return strokes.size + images.size + texts.size
    }

    /**
     * Replaces strokes and pictures in place (same IDs) as ONE undo step: the lasso moving,
     * resizing or turning a selection of both.
     */
    fun replace(pageId: String, strokes: List<InkStroke>, images: List<InkImage>, texts: List<InkText> = emptyList()) {
        val edits = buildList {
            val list = pages[pageId].orEmpty()
            val byId = strokes.associateBy { it.id }
            val before = list.filter { it.id in byId }
            if (before.isNotEmpty()) add(InkEdit.Replace(pageId, before, before.map { byId.getValue(it.id) }))
            val imgs = pictures[pageId].orEmpty()
            val imgById = images.associateBy { it.id }
            val imgBefore = imgs.filter { it.id in imgById }
            if (imgBefore.isNotEmpty()) add(InkEdit.ReplaceImages(pageId, imgBefore, imgBefore.map { imgById.getValue(it.id) }))
            val boxes = textBoxes[pageId].orEmpty()
            val textById = texts.associateBy { it.id }
            val textBefore = boxes.filter { it.id in textById }
            if (textBefore.isNotEmpty()) add(InkEdit.ReplaceTexts(pageId, textBefore, textBefore.map { textById.getValue(it.id) }))
        }
        recordAll(edits)
    }

    /** Draws strokes on several pages as ONE undo step (a stroke split at a page edge, F36). */
    fun addAcross(byPage: Map<String, List<InkStroke>>) {
        recordAll(byPage.filterValues { it.isNotEmpty() }.map { (pageId, strokes) -> InkEdit.Add(pageId, strokes) })
    }

    /** Adds strokes and pictures as ONE undo step (the lasso's Paste and Duplicate). */
    fun add(pageId: String, strokes: List<InkStroke>, images: List<InkImage>, texts: List<InkText> = emptyList()) {
        recordAll(
            buildList {
                if (strokes.isNotEmpty()) add(InkEdit.Add(pageId, strokes))
                if (images.isNotEmpty()) add(InkEdit.AddImages(pageId, images))
                if (texts.isNotEmpty()) add(InkEdit.AddTexts(pageId, texts))
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
    fun erase(pageId: String, ids: Set<String>, drag: Long? = null): Int {
        val list = pages[pageId] ?: return 0
        val removed = list.withIndex().filter { it.value.id in ids }
        if (removed.isEmpty()) return 0
        record(InkEdit.Remove(pageId, removed), drag)
        return removed.size
    }

    /**
     * The partial eraser (Phase 7, 7.4): each stroke in [cuts] (by ID) is replaced by its pieces,
     * in its place in the drawing order. Edits with the same [drag] (one eraser drag) in a row
     * become ONE undo step. Returns how many strokes changed.
     */
    fun split(pageId: String, cuts: Map<String, List<InkStroke>>, drag: Long? = null): Int {
        val list = pages[pageId] ?: return 0
        // Highest index first, so each Split's index is still right when it's applied.
        val edits = list.withIndex().filter { it.value.id in cuts }.sortedByDescending { it.index }
            .map { (index, stroke) -> InkEdit.Split(pageId, index, stroke, cuts.getValue(stroke.id)) }
        if (edits.isEmpty()) return 0
        record(edits.singleOrNull() ?: InkEdit.Group(edits), drag)
        return edits.size
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
        openDrag = null
        require(
            edit is InkEdit.AddPage || edit is InkEdit.DeletePage || edit is InkEdit.PagePaper || edit is InkEdit.NotebookPaper,
        )
        undoStack.addLast(edit)
        trim()
        redoStack.clear()
    }

    /** Undoes the last edit and returns it (the caller saves its page, or restores/removes a page). */
    fun undo(): InkEdit? {
        openDrag = null
        val edit = undoStack.removeLastOrNull() ?: return null
        revert(edit)
        redoStack.addLast(edit)
        return edit
    }

    /** Redoes the last undone edit and returns it. */
    fun redo(): InkEdit? {
        openDrag = null
        val edit = redoStack.removeLastOrNull() ?: return null
        apply(edit)
        undoStack.addLast(edit)
        return edit
    }

    /** The eraser drag the last undo step belongs to (Phase 7, 7.4); null after any other edit. */
    private var openDrag: Long? = null

    private fun record(edit: InkEdit, drag: Long? = null) {
        apply(edit)
        val last = undoStack.lastOrNull()
        if (drag != null && drag == openDrag && last != null) {
            // The same eraser drag: one undo step for the whole drag.
            val before = if (last is InkEdit.Group) last.edits else listOf(last)
            undoStack.removeLast()
            undoStack.addLast(InkEdit.Group(before + edit))
        } else {
            undoStack.addLast(edit)
            trim()
        }
        openDrag = drag
        redoStack.clear()
    }

    private fun trim() {
        while (undoStack.size > maxUndo) undoStack.removeFirst()
    }

    private fun page(pageId: String) = pages.getOrPut(pageId) { mutableListOf() }
    private fun picturesOf(pageId: String) = pictures.getOrPut(pageId) { mutableListOf() }
    private fun textsOf(pageId: String) = textBoxes.getOrPut(pageId) { mutableListOf() }

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
            is InkEdit.AddTexts -> textsOf(edit.pageId).addAll(edit.texts)
            is InkEdit.RemoveTexts -> {
                val ids = edit.removed.map { it.value.id }.toSet()
                textsOf(edit.pageId).removeAll { it.id in ids }
            }
            is InkEdit.ReplaceTexts -> swapTexts(edit.pageId, edit.after)
            is InkEdit.Group -> edit.edits.forEach { apply(it) }
            is InkEdit.Split -> {
                val list = page(edit.pageId)
                val at = list.indexOfFirst { it.id == edit.original.id }
                if (at >= 0) {
                    list.removeAt(at)
                    list.addAll(at, edit.pieces)
                }
            }
            is InkEdit.AddPage, is InkEdit.DeletePage, is InkEdit.PagePaper, is InkEdit.NotebookPaper -> Unit
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
            is InkEdit.AddTexts -> {
                val ids = edit.texts.map { it.id }.toSet()
                textsOf(edit.pageId).removeAll { it.id in ids }
            }
            is InkEdit.RemoveTexts -> {
                val list = textsOf(edit.pageId)
                for ((index, text) in edit.removed.sortedBy { it.index }) list.add(index.coerceAtMost(list.size), text)
            }
            is InkEdit.ReplaceTexts -> swapTexts(edit.pageId, edit.before)
            is InkEdit.Group -> edit.edits.asReversed().forEach { revert(it) }
            is InkEdit.Split -> {
                val list = page(edit.pageId)
                val ids = edit.pieces.map { it.id }.toSet()
                val at = list.indexOfFirst { it.id in ids }.takeIf { it >= 0 } ?: edit.index
                list.removeAll { it.id in ids }
                list.add(at.coerceIn(0, list.size), edit.original)
            }
            is InkEdit.AddPage, is InkEdit.DeletePage, is InkEdit.PagePaper, is InkEdit.NotebookPaper -> Unit
        }
    }

    private fun swapImages(pageId: String, replacements: List<InkImage>) {
        val byId = replacements.associateBy { it.id }
        val list = picturesOf(pageId)
        for (i in list.indices) byId[list[i].id]?.let { list[i] = it }
    }

    private fun swapTexts(pageId: String, replacements: List<InkText>) {
        val byId = replacements.associateBy { it.id }
        val list = textsOf(pageId)
        for (i in list.indices) byId[list[i].id]?.let { list[i] = it }
    }

    private fun swap(pageId: String, replacements: List<InkStroke>) {
        val byId = replacements.associateBy { it.id }
        val list = page(pageId)
        for (i in list.indices) byId[list[i].id]?.let { list[i] = it }
    }
}
