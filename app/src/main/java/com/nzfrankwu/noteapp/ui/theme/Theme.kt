package com.nzfrankwu.noteapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.nzfrankwu.noteapp.domain.CourseColorKey
import com.nzfrankwu.noteapp.domain.CourseSeen

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Color(0xFF0D2557),
    secondary = LightOnSurfaceVariant,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceHigh,
    onSecondaryContainer = LightOnSurface,
    tertiary = Blue40,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceDim,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightSurfaceHigh,
    error = Red40,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Color(0xFF0D2557),
    primaryContainer = Blue30,
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = DarkOnSurfaceVariant,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceHigh,
    onSecondaryContainer = DarkOnSurface,
    tertiary = Blue80,
    onTertiary = Color(0xFF0D2557),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceDim,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = DarkSurfaceHigh,
    error = Red80,
    onError = Color(0xFF690005),
)

/**
 * True while the app is shown dark. Read this, never `isSystemInDarkTheme()`, so the Light/Dark
 * choice in Settings (Phase 7, 7.6) reaches every screen (the editor's desk and page colours too).
 */
val LocalDarkTheme = androidx.compose.runtime.staticCompositionLocalOf { false }

/** NoteApp's theme. Light or dark as chosen ([darkTheme]); colours are fixed (see Color.kt). */
@Composable
fun NoteAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Every saved course, so each gets the next free colour in the order it was first seen. */
    courses: List<CourseSeen> = emptyList(),
    content: @Composable () -> Unit,
) {
    val basePalette = if (darkTheme) DarkCourseColors else LightCourseColors
    val courseColors = remember(basePalette, courses) {
        basePalette.copy(slots = CourseColorKey.slotsInOrder(courses, basePalette.palette.size))
    }
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalCourseColors provides courseColors,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            shapes = NoteappShapes,
            content = content,
        )
    }
}
