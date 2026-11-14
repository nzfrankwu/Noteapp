package com.nzfrankwu.noteapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nzfrankwu.noteapp.domain.TaskStatus

/** A background colour and the text colour that goes on it. */
@Immutable
data class TagColors(val container: Color, val content: Color)

/**
 * The colours of the status tags and the Overdue badge, for light and dark mode.
 * Read them with `LocalStatusColors.current` (NoteAppTheme provides the right set).
 * To restyle a status, change its colours here (the values live in Color.kt).
 */
@Immutable
data class StatusColors(
    val notStarted: TagColors,
    val started: TagColors,
    val stuck: TagColors,
    val doubleChecking: TagColors,
    val submitted: TagColors,
    val done: TagColors,
    val overdue: TagColors,
) {
    fun of(status: TaskStatus): TagColors = when (status) {
        TaskStatus.NOT_STARTED -> notStarted
        TaskStatus.STARTED -> started
        TaskStatus.STUCK -> stuck
        TaskStatus.DOUBLE_CHECKING -> doubleChecking
        TaskStatus.SUBMITTED -> submitted
        TaskStatus.DONE -> done
    }
}

val LightStatusColors = StatusColors(
    notStarted = TagColors(LightSurfaceHigh, LightOnSurfaceVariant),
    started = TagColors(Blue90, Color(0xFF0D2557)),
    stuck = TagColors(AmberContainerLight, AmberOnLight),
    doubleChecking = TagColors(PurpleContainerLight, PurpleOnLight),
    submitted = TagColors(TealContainerLight, TealOnLight),
    done = TagColors(GreenContainerLight, GreenOnLight),
    overdue = TagColors(RedContainerLight, RedOnLight),
)

val DarkStatusColors = StatusColors(
    notStarted = TagColors(DarkSurfaceHigh, DarkOnSurfaceVariant),
    started = TagColors(Blue30, Color(0xFFDCE6FF)),
    stuck = TagColors(AmberContainerDark, AmberOnDark),
    doubleChecking = TagColors(PurpleContainerDark, PurpleOnDark),
    submitted = TagColors(TealContainerDark, TealOnDark),
    done = TagColors(GreenContainerDark, GreenOnDark),
    overdue = TagColors(RedContainerDark, RedOnDark),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
