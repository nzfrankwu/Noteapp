package com.nzfrankwu.noteapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nzfrankwu.noteapp.domain.CourseCodes
import com.nzfrankwu.noteapp.domain.CourseColorKey

/**
 * One course's colours.
 * - [accent]: the small bar beside the course code (and later calendar chip bars).
 * - [text]: the course code written in the course's colour, readable on cards.
 * - [container]: a soft fill, e.g. timeline bars.
 */
@Immutable
data class CourseColor(val accent: Color, val text: Color, val container: Color)

/**
 * The automatic course palette, in light and dark versions. Read it with
 * `LocalCourseColors.current.of(code)` (NoteAppTheme provides the right set).
 *
 * Courses take the colours in the order Noteapp first saw them ([slots], see [CourseColorKey]),
 * so neighbouring colours in the list are far apart in hue: any few courses seen one after
 * another look clearly different. Reordering, adding or removing colours changes courses'
 * colours; changing a shade is fine.
 * Red, orange and amber are left out so a course tag never looks like "overdue" or "due soon".
 */
@Immutable
data class CourseColors(
    val palette: List<CourseColor>,
    val none: CourseColor,
    /** Each known course's slot, keyed by [CourseCodes.matchKey] (from [CourseColorKey.slotsInOrder]). */
    val slots: Map<String, Int> = emptyMap(),
) {
    /**
     * The colours for a course code, or neutral grey for tasks without a course. A code that isn't
     * a saved course yet gets a fixed fallback colour ([CourseColorKey.slot]).
     */
    fun of(code: String?): CourseColor {
        if (code == null) return none
        val slot = slots[CourseCodes.matchKey(code)] ?: CourseColorKey.slot(code, palette.size)
        return palette[slot % palette.size]
    }
}

val LightCourseColors = CourseColors(
    palette = listOf(
        CourseColor(Color(0xFF4F5FD0), Color(0xFF3442A8), Color(0xFFDFE2FF)), // indigo
        CourseColor(Color(0xFF8FA300), Color(0xFF5A6A00), Color(0xFFEAF0B8)), // olive
        CourseColor(Color(0xFF0598C9), Color(0xFF00708E), Color(0xFFCDEEFA)), // sky
        CourseColor(Color(0xFFC2369B), Color(0xFF9C1E7C), Color(0xFFFFD7F1)), // magenta
        CourseColor(Color(0xFF00897B), Color(0xFF00695C), Color(0xFFCDEFEA)), // teal
        CourseColor(Color(0xFF8E4FC9), Color(0xFF6A2FA8), Color(0xFFEFDDFF)), // purple
        CourseColor(Color(0xFF43A047), Color(0xFF2E7D32), Color(0xFFD7F0D2)), // green
    ),
    none = CourseColor(LightOutline, LightOnSurfaceVariant, LightSurfaceHigh),
)

val DarkCourseColors = CourseColors(
    palette = listOf(
        CourseColor(Color(0xFF9AA5FF), Color(0xFFB8C0FF), Color(0xFF2B3478)), // indigo
        CourseColor(Color(0xFFC5D64A), Color(0xFFD8E47E), Color(0xFF3E4510)), // olive
        CourseColor(Color(0xFF5CCBF0), Color(0xFF8ADAF5), Color(0xFF0B4558)), // sky
        CourseColor(Color(0xFFF27BD3), Color(0xFFFFA3E6), Color(0xFF5A1A4C)), // magenta
        CourseColor(Color(0xFF4DD0BF), Color(0xFF80DECF), Color(0xFF0F4A44)), // teal
        CourseColor(Color(0xFFC79BF5), Color(0xFFDABBFF), Color(0xFF452A6E)), // purple
        CourseColor(Color(0xFF7FD283), Color(0xFFA5E0A8), Color(0xFF1F4A22)), // green
    ),
    none = CourseColor(DarkOutline, DarkOnSurfaceVariant, DarkSurfaceHigh),
)

val LocalCourseColors = staticCompositionLocalOf { LightCourseColors }
