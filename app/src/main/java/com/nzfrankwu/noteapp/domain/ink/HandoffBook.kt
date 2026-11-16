package com.nzfrankwu.noteapp.domain.ink

/**
 * The bookkeeping behind the pen-up handoff (feedback F30, F36), kept in plain Kotlin so it can be
 * tested. Each pen-layer stroke [W] is saved as one or more pieces (a piece per page when it
 * crossed a page edge); the pen layer may let go of the stroke once every piece has been drawn by
 * its page. Not thread-safe: main thread only.
 */
class HandoffBook<W : Any> {
    private val waiting = HashMap<String, W>()
    private val piecesLeft = HashMap<W, Int>()

    val hasWaiting: Boolean get() = waiting.isNotEmpty()

    /** Stroke [wet] was saved as [pieceIds]. False if there are none (let go of it at once). */
    fun expect(pieceIds: List<String>, wet: W): Boolean {
        if (pieceIds.isEmpty()) return false
        for (id in pieceIds) waiting[id] = wet
        piecesLeft[wet] = pieceIds.size
        return true
    }

    /**
     * Piece [pieceId] was drawn. Returns the stroke to let go of when that was its last piece
     * still waiting, else null (also for a piece already counted or not waited for).
     */
    fun drawn(pieceId: String): W? {
        val wet = waiting.remove(pieceId) ?: return null
        val left = (piecesLeft[wet] ?: 1) - 1
        if (left > 0) {
            piecesLeft[wet] = left
            return null
        }
        return wet
    }

    /** Forgets [wet] (let go of). True the first time; false if it was already let go of. */
    fun release(wet: W): Boolean {
        if (piecesLeft.remove(wet) == null) return false
        waiting.values.removeAll { it == wet }
        return true
    }
}
