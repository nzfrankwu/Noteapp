package com.nzfrankwu.noteapp.domain

import kotlin.math.abs
import kotlin.math.min

/**
 * Forgiving word matching for handwriting search (messy writing, PHASE-7.md 7.10 follow-up).
 * Plain Kotlin, unit-tested.
 *
 * A search word matches recognised handwriting when it's in the text as typed (any case), or when
 * some word of the text is only a letter or two different: 1 for words of 4–6 letters, 2 for 7 or
 * more. Words of 1–3 letters must match exactly (too many short words are one letter apart).
 */
object FuzzyWords {
    /** How many letters may differ for a search word of this length. */
    fun allowed(length: Int): Int = when {
        length < 4 -> 0
        length < 7 -> 1
        else -> 2
    }

    /** The words of [text], lower case (letters and digits only). */
    fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /** True when [word] is in [text] exactly, or close to one of [tokens] (from [text]). */
    fun matches(word: String, text: String, tokens: List<String>): Boolean {
        if (text.contains(word, ignoreCase = true)) return true
        val w = word.lowercase()
        val max = allowed(w.length)
        if (max == 0) return false
        return tokens.any { abs(it.length - w.length) <= max && distance(w, it, max) <= max }
    }

    /** True when every one of [words] matches [text] (see [matches]). */
    fun matchesAll(words: List<String>, text: String): Boolean {
        if (words.isEmpty()) return false
        val tokens = tokens(text)
        return words.all { matches(it, text, tokens) }
    }

    /**
     * Edit distance between [a] and [b] (letters added, removed or changed), stopping early once
     * it's over [limit] (then any number above [limit] is returned).
     */
    fun distance(a: String, b: String, limit: Int): Int {
        if (abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var best = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                if (cur[j] < best) best = cur[j]
            }
            if (best > limit) return limit + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
