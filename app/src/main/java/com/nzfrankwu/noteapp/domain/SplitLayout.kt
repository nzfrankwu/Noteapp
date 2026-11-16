package com.nzfrankwu.noteapp.domain

/**
 * Two notebooks open in the editor at once (PHASE-5.md 5.8): how they sit, and the divider's
 * rules. Plain Kotlin, unit-tested.
 */
enum class SplitMode(val key: String) {
    /** Left and right. */
    SIDE_BY_SIDE("side"),

    /** Top and bottom. */
    TOP_BOTTOM("stacked");

    companion object {
        fun fromKey(key: String?): SplitMode? = entries.firstOrNull { it.key == key }

        /** The first split's mode before one was chosen: side by side when wide, else stacked. */
        fun forShape(width: Float, height: Float): SplitMode = if (width >= height) SIDE_BY_SIDE else TOP_BOTTOM
    }
}

/** Which of the two panes. */
enum class PaneSide { FIRST, SECOND }

/** What letting go of the divider at a place does. */
sealed interface DividerRelease {
    /** The divider stays at [ratio] (the first pane's share, 0–1). */
    data class Keep(val ratio: Float) : DividerRelease

    /** The pane on [side] is squeezed shut: split view ends, the other notebook stays. */
    data class Close(val side: PaneSide) : DividerRelease
}

object SplitLayout {
    /** The divider moves freely between these shares of the first pane. */
    const val MIN_RATIO = 0.25f
    const val MAX_RATIO = 0.75f

    /** Let go closer than this to an edge and the squeezed pane closes. */
    const val CLOSE_ZONE = 0.08f

    const val DEFAULT_RATIO = 0.5f

    /** The window must be at least this big along its longer side for two notebooks (dp). */
    const val MIN_SPLIT_DP = 840f

    /** Where the divider is drawn while it's dragged to [ratio]: anywhere short of the very edge. */
    fun dragged(ratio: Float): Float = ratio.coerceIn(0.03f, 0.97f)

    /** True while a drag at [ratio] would close a pane if let go now ("Release to close"). */
    fun closing(ratio: Float): Boolean = ratio < CLOSE_ZONE || ratio > 1f - CLOSE_ZONE

    /** Letting go at [ratio]: close a squeezed pane, or keep a share between MIN and MAX. */
    fun release(ratio: Float): DividerRelease = when {
        ratio < CLOSE_ZONE -> DividerRelease.Close(PaneSide.FIRST)
        ratio > 1f - CLOSE_ZONE -> DividerRelease.Close(PaneSide.SECOND)
        else -> DividerRelease.Keep(ratio.coerceIn(MIN_RATIO, MAX_RATIO))
    }

    /** A saved share, made safe (old or odd values come back to the middle). */
    fun saved(ratio: Float?): Float = ratio?.takeIf { it in MIN_RATIO..MAX_RATIO } ?: DEFAULT_RATIO

    /** Whether two notebooks fit in a window [widthDp] × [heightDp]. */
    fun canSplit(widthDp: Float, heightDp: Float): Boolean = maxOf(widthDp, heightDp) >= MIN_SPLIT_DP

    /**
     * Which half a notebook dropped at ([x], [y]) inside an area [width] × [height] lands in:
     * left/right side by side, top/bottom stacked.
     */
    fun dropSide(x: Float, y: Float, width: Float, height: Float, mode: SplitMode): PaneSide = when (mode) {
        SplitMode.SIDE_BY_SIDE -> if (x < width / 2) PaneSide.FIRST else PaneSide.SECOND
        SplitMode.TOP_BOTTOM -> if (y < height / 2) PaneSide.FIRST else PaneSide.SECOND
    }
}

/** The recently opened notebooks (PHASE-5.md 5.7): newest first, no repeats, at most [MAX]. */
object RecentNotebooks {
    const val MAX = 30

    fun opened(list: List<String>, id: String): List<String> = (listOf(id) + list.filter { it != id }).take(MAX)

    /** Saved as one line of IDs separated by commas (IDs are UUIDs, so no commas in them). */
    fun encode(list: List<String>): String = list.joinToString(",")

    fun decode(text: String?): List<String> = text.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX)
}
