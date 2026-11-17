package com.nzfrankwu.noteapp.domain

/**
 * Light or dark (Settings → Appearance, Phase 7, 7.6). [key] is saved in the settings, so it must
 * never change (StoredKeysTest).
 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Like the tablet"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    /** Dark or not, given whether the tablet is in dark mode. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** Unknown or missing keys follow the tablet. */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
