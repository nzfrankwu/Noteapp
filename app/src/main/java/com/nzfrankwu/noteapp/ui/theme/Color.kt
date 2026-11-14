package com.nzfrankwu.noteapp.ui.theme

import androidx.compose.ui.graphics.Color

// NoteApp's fixed palette: neutral greys with one blue accent.
// Every screen takes its colours from here (via MaterialTheme.colorScheme), so changing a
// value below restyles the whole app consistently. Wallpaper-based "dynamic" colours are
// deliberately not used, so the app looks the same on every tablet.

// Accent blue
val Blue40 = Color(0xFF2F6BDB)   // light-mode accent (buttons, selected items)
val Blue80 = Color(0xFFA9C4FF)   // dark-mode accent
val Blue90 = Color(0xFFDCE6FF)   // light-mode selected/highlight background
val Blue30 = Color(0xFF1E3A6E)   // dark-mode selected/highlight background (navy)

// Light mode neutrals
val LightBackground = Color(0xFFF7F7F8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFF0F1F3)
val LightSurfaceHigh = Color(0xFFE9EAED)
val LightOnSurface = Color(0xFF1B1C1E)
val LightOnSurfaceVariant = Color(0xFF5D6066)
val LightOutline = Color(0xFFC4C6CC)

// Dark mode neutrals (true near-black, no colour tint)
val DarkBackground = Color(0xFF121214)
val DarkSurface = Color(0xFF1C1C1F)
val DarkSurfaceDim = Color(0xFF18181B)
val DarkSurfaceHigh = Color(0xFF2A2A2E)
val DarkOnSurface = Color(0xFFE6E6E9)
val DarkOnSurfaceVariant = Color(0xFFA3A5AB)
val DarkOutline = Color(0xFF45474D)

// Status colours (used for "Overdue" later)
val Red40 = Color(0xFFC62828)
val Red80 = Color(0xFFFFB4AB)
