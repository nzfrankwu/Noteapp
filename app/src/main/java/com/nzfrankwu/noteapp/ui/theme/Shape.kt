package com.nzfrankwu.noteapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner rounding for the whole app. Components ask for MaterialTheme.shapes.medium etc., so
 * changing a value here changes every tile, card, panel and dialog that uses it.
 */
val NoteappShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), // chips, small badges
    small = RoundedCornerShape(10.dp), // text fields, small buttons
    medium = RoundedCornerShape(14.dp), // folder tiles, task cards, settings groups
    large = RoundedCornerShape(18.dp), // menus, sheets
    extraLarge = RoundedCornerShape(22.dp), // big panels (Due soon), dialogs
)
