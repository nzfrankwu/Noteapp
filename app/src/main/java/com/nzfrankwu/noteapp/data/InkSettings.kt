package com.nzfrankwu.noteapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nzfrankwu.noteapp.domain.ink.EditorTool
import com.nzfrankwu.noteapp.domain.ink.FitMode
import com.nzfrankwu.noteapp.domain.ink.InkToolSettings
import com.nzfrankwu.noteapp.domain.ink.PenPressure
import com.nzfrankwu.noteapp.domain.ink.PenStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The notebook toolbar's remembered choices. */
interface InkSettingsStore {
    val settings: Flow<InkToolSettings>
    suspend fun update(change: (InkToolSettings) -> InkToolSettings)
}

/** Keeps the choices in memory only (screen tests). */
class InMemoryInkSettings(initial: InkToolSettings = InkToolSettings()) : InkSettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<InkToolSettings> = state
    override suspend fun update(change: (InkToolSettings) -> InkToolSettings) = state.update(change)
}

private val Context.inkSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "ink_settings")

/** The toolbar's choices, in Noteapp's private storage. Nothing secret here. */
class InkSettings(context: Context) : InkSettingsStore {
    private val store = context.applicationContext.inkSettingsStore

    override val settings: Flow<InkToolSettings> = store.data.map(::read)

    override suspend fun update(change: (InkToolSettings) -> InkToolSettings) {
        store.edit { prefs -> write(prefs, change(read(prefs))) }
    }

    private fun read(p: Preferences): InkToolSettings {
        val d = InkToolSettings()
        return InkToolSettings(
            tool = EditorTool.fromKey(p[TOOL]),
            pens = d.pens.mapIndexed { i, pen ->
                val color = com.nzfrankwu.noteapp.domain.ink.PenDefaults.savedColor(p[penColor(i)], p[PENS_VERSION], i)
                PenStyle(color, p[penSize(i)] ?: pen.size, (p[penPressure(i)] ?: pen.pressure).coerceIn(PenPressure.OFF, PenPressure.FULL))
            },
            highlighter = PenStyle(p[HIGHLIGHTER_COLOR] ?: d.highlighter.colorArgb, p[HIGHLIGHTER_SIZE] ?: d.highlighter.size),
            eraserRadius = p[ERASER_RADIUS] ?: d.eraserRadius,
            pressureErase = p[PRESSURE_ERASE] ?: d.pressureErase,
            pressureEraseLevel = p[PRESSURE_LEVEL] ?: d.pressureEraseLevel,
            pressureEraseHoldMs = p[PRESSURE_HOLD_MS] ?: d.pressureEraseHoldMs,
            stability = p[STABILITY] ?: d.stability,
            holdToStraighten = p[HOLD_TO_STRAIGHTEN] ?: d.holdToStraighten,
            highlighterRound = p[HIGHLIGHTER_ROUND] ?: d.highlighterRound,
            toolbarX = p[TOOLBAR_X] ?: d.toolbarX,
            toolbarY = p[TOOLBAR_Y] ?: d.toolbarY,
            toolbarCollapsed = p[TOOLBAR_COLLAPSED] ?: d.toolbarCollapsed,
            fitMode = FitMode.fromKey(p[FIT_MODE]),
            drawWithFinger = p[DRAW_WITH_FINGER] ?: d.drawWithFinger,
            customColors = p[CUSTOM_COLORS]?.let(::parseColors) ?: d.customColors,
            penPalette = p[PEN_PALETTE]?.let(::parseColors)?.takeIf { it.isNotEmpty() } ?: d.penPalette,
            highlighterPalette = p[HIGHLIGHTER_PALETTE]?.let(::parseColors)?.takeIf { it.isNotEmpty() } ?: d.highlighterPalette,
            bookmarkRect = p[BOOKMARK_RECT] ?: d.bookmarkRect,
        )
    }

    private fun write(p: MutablePreferences, s: InkToolSettings) {
        p[TOOL] = s.tool.key
        s.pens.forEachIndexed { i, pen ->
            p[penColor(i)] = pen.colorArgb
            p[penSize(i)] = pen.size
            p[penPressure(i)] = pen.pressure
        }
        p[HIGHLIGHTER_COLOR] = s.highlighter.colorArgb
        p[HIGHLIGHTER_SIZE] = s.highlighter.size
        p[ERASER_RADIUS] = s.eraserRadius
        p[PRESSURE_ERASE] = s.pressureErase
        p[PRESSURE_LEVEL] = s.pressureEraseLevel
        p[PRESSURE_HOLD_MS] = s.pressureEraseHoldMs
        p[STABILITY] = s.stability
        p[HOLD_TO_STRAIGHTEN] = s.holdToStraighten
        p[HIGHLIGHTER_ROUND] = s.highlighterRound
        p[TOOLBAR_X] = s.toolbarX
        p[TOOLBAR_Y] = s.toolbarY
        p[TOOLBAR_COLLAPSED] = s.toolbarCollapsed
        p[FIT_MODE] = s.fitMode.key
        p[DRAW_WITH_FINGER] = s.drawWithFinger
        p[CUSTOM_COLORS] = s.customColors.joinToString(",") { Integer.toHexString(it) }
        p[PEN_PALETTE] = s.penPalette.joinToString(",") { Integer.toHexString(it) }
        p[HIGHLIGHTER_PALETTE] = s.highlighterPalette.joinToString(",") { Integer.toHexString(it) }
        p[BOOKMARK_RECT] = s.bookmarkRect
        p[PENS_VERSION] = com.nzfrankwu.noteapp.domain.ink.PenDefaults.VERSION
    }

    /** Colours saved as "ff1b1c1e,ff2f6bdb"; anything unreadable is skipped. */
    private fun parseColors(text: String): List<Int> =
        text.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toLongOrNull(16)?.toInt() }

    private companion object {
        val TOOL = stringPreferencesKey("tool")
        fun penColor(i: Int) = intPreferencesKey("pen${i + 1}_color")
        fun penSize(i: Int) = floatPreferencesKey("pen${i + 1}_size")
        fun penPressure(i: Int) = intPreferencesKey("pen${i + 1}_pressure")
        val PEN_PALETTE = stringPreferencesKey("pen_palette")
        val HIGHLIGHTER_PALETTE = stringPreferencesKey("highlighter_palette")
        val HIGHLIGHTER_COLOR = intPreferencesKey("highlighter_color")
        val HIGHLIGHTER_SIZE = floatPreferencesKey("highlighter_size")
        val ERASER_RADIUS = floatPreferencesKey("eraser_radius")
        val PRESSURE_ERASE = booleanPreferencesKey("pressure_erase")
        val PRESSURE_LEVEL = floatPreferencesKey("pressure_erase_level")
        val PRESSURE_HOLD_MS = intPreferencesKey("pressure_erase_hold_ms")
        val CUSTOM_COLORS = stringPreferencesKey("custom_colors")
        val STABILITY = intPreferencesKey("stability")
        val HOLD_TO_STRAIGHTEN = booleanPreferencesKey("hold_to_straighten")
        val HIGHLIGHTER_ROUND = booleanPreferencesKey("highlighter_round")
        val TOOLBAR_X = floatPreferencesKey("toolbar_x")
        val TOOLBAR_Y = floatPreferencesKey("toolbar_y")
        val TOOLBAR_COLLAPSED = booleanPreferencesKey("toolbar_collapsed")
        val FIT_MODE = stringPreferencesKey("fit_mode")
        val DRAW_WITH_FINGER = booleanPreferencesKey("draw_with_finger")
        val BOOKMARK_RECT = booleanPreferencesKey("bookmark_rect")

        /** Which pen defaults the saved pens were chosen under (see PenDefaults). */
        val PENS_VERSION = intPreferencesKey("pens_version")
    }
}
