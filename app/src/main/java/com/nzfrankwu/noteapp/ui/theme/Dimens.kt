package com.nzfrankwu.noteapp.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Every spacing and size used across the app, in one place, so the whole look can be retuned
 * here. (Colours are in Color.kt, corner shapes in Shape.kt, text styles in Type.kt.)
 */
object Dimens {
    /** Gap between the screen edge and content. */
    val ScreenPadding = 16.dp

    /** Gap between the two home panes, and between stacked sections. */
    val PaneGap = 16.dp

    /** Inside padding of a shaded panel (e.g. Due soon) or a tile. */
    val PanelPadding = 16.dp
    val TilePadding = 14.dp

    /** Gap between items in lists and grids. */
    val ItemGap = 12.dp

    /** Smallest folder tile width; the grid fits as many tiles per row as the width allows. */
    val FolderTileMinWidth = 180.dp
    val FolderTileHeight = 132.dp
    val FolderTileIconSize = 32.dp

    /** Minimum height of the Due soon panel in the stacked (narrow) home layout. */
    val NarrowPanelMinHeight = 200.dp

    /** Max width of reading-style pages (Settings, forms) so they don't stretch on wide screens. */
    val ReadingMaxWidth = 720.dp

    /** Top/bottom padding inside a task card. */
    val TaskCardVerticalPadding = 12.dp

    /** Smallest height of a status tag or badge ("Started", "Overdue"). */
    val TagHeight = 26.dp

    /** Gap between fields in a form (New task, Edit task). */
    val FieldGap = 14.dp
}
