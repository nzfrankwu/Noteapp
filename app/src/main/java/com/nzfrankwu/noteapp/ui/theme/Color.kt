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

// Error / Overdue
val Red40 = Color(0xFFC62828)
val Red80 = Color(0xFFFFB4AB)

// Status tag colours: a soft background ("Container") with darker text ("On") in light mode,
// and the reverse in dark mode. Used by StatusChip and the Overdue badge (see StatusColors.kt).
val AmberContainerLight = Color(0xFFFFE6C2)
val AmberOnLight = Color(0xFF6B3F00)
val AmberContainerDark = Color(0xFF55380A)
val AmberOnDark = Color(0xFFFFDDB0)

// "Due soon" text (e.g. "Due tomorrow") straight on a card, without a tag background.
val AmberTextLight = Color(0xFF8A5300)
val AmberTextDark = Color(0xFFFFC27A)

val PurpleContainerLight = Color(0xFFEBDFFF)
val PurpleOnLight = Color(0xFF3F2178)
val PurpleContainerDark = Color(0xFF45307A)
val PurpleOnDark = Color(0xFFEBDFFF)

val TealContainerLight = Color(0xFFD0F0E3)
val TealOnLight = Color(0xFF0B5136)
val TealContainerDark = Color(0xFF124A35)
val TealOnDark = Color(0xFFB6EBD3)

val GreenContainerLight = Color(0xFFDCEFD3)
val GreenOnLight = Color(0xFF2C5A1D)
val GreenContainerDark = Color(0xFF2A4720)
val GreenOnDark = Color(0xFFCDEBC0)

val RedContainerLight = Color(0xFFFFDAD6)
val RedOnLight = Color(0xFF8C0009)
val RedContainerDark = Color(0xFF7A1A14)
val RedOnDark = Color(0xFFFFDAD6)

// Notebook paper: white with soft blue-grey lines in BOTH light and dark mode, like real paper
// (and like the PDF copies in Phase 6). Ink colours are chosen to read well on it.
val PaperWhite = Color(0xFFFFFFFF)
val PaperLine = Color(0xFFC9D1DC)
// Lines on small paper previews (e.g. New notebook), where the normal lines would vanish.
val PaperLineStrong = Color(0xFF9DA9B8)
// The desk the pages lie on in the notebook editor.
val DeskLight = Color(0xFF3A3B3F) // dark grey round the pages, in both themes (F25)
val DeskDark = Color(0xFF202124)

/** The notebook editor's top bar: dark in both themes, like Notein (PHASE-2.md F5). */
val EditorBarButton = Color(0xFF2E2E31)
val EditorBarIcon = Color(0xFFF2F2F5)
val EditorBarIconDisabled = Color(0x66F2F2F5)
