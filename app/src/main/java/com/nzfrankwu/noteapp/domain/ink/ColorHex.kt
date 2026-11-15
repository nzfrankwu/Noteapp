package com.nzfrankwu.noteapp.domain.ink

/** Colours as hex text, like "#1A3A8F" (the colour wheel's box, PHASE-2.md F10). Always opaque. */
object ColorHex {
    /** "#RRGGBB" for an 0xAARRGGBB colour (the alpha is left out). */
    fun format(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /**
     * The opaque colour for "#RRGGBB", "RRGGBB" or the short "#RGB" (case and spaces don't
     * matter), or null if it isn't a colour.
     */
    fun parse(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        val full = when (hex.length) {
            6 -> hex
            3 -> hex.map { "$it$it" }.joinToString("")
            else -> return null
        }
        if (!full.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return (0xFF000000.toInt()) or full.toInt(16)
    }
}
