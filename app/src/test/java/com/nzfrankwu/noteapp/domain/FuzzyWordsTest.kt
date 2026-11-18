package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forgiving matching for messy handwriting. */
class FuzzyWordsTest {
    @Test
    fun aLetterOrTwoOff_stillMatches_dependingOnLength() {
        assertTrue(FuzzyWords.matchesAll(listOf("smith"), "The Smlth chart"))         // 1 change, 5 letters
        assertTrue(FuzzyWords.matchesAll(listOf("impedance"), "impedence matching"))  // 1 change
        assertTrue(FuzzyWords.matchesAll(listOf("transmission"), "transmision lnes"))  // 1 missing letter
        assertTrue(FuzzyWords.matchesAll(listOf("transmission"), "tramsmision"))       // 2 off, 12 letters
        assertFalse(FuzzyWords.matchesAll(listOf("smith"), "The Snlth chart"))         // 2 off, 5 letters
        assertFalse(FuzzyWords.matchesAll(listOf("lab"), "lob 2 report"))              // short words exact only
        assertTrue(FuzzyWords.matchesAll(listOf("lab"), "LAB 2"))
    }

    @Test
    fun everyWord_mustMatch_andPartsOfWordsStillCount() {
        assertTrue(FuzzyWords.matchesAll(listOf("smith", "chart"), "smlth chart"))
        assertFalse(FuzzyWords.matchesAll(listOf("smith", "fourier"), "smlth chart"))
        assertTrue("exact part of a word", FuzzyWords.matchesAll(listOf("mission"), "transmission"))
        assertFalse(FuzzyWords.matchesAll(emptyList(), "anything"))
    }

    @Test
    fun distance_isTheNumberOfLetterChanges_cutOffAtTheLimit() {
        assertEquals(0, FuzzyWords.distance("chart", "chart", 2))
        assertEquals(1, FuzzyWords.distance("chart", "chort", 2))
        assertEquals(2, FuzzyWords.distance("smith", "smth", 2).coerceAtMost(2).let { FuzzyWords.distance("smith", "snth", 2) })
        assertEquals(3, FuzzyWords.distance("abcdef", "uvwxyz", 2)) // over the limit: limit + 1
        assertEquals(listOf("the", "smith", "chart", "2"), FuzzyWords.tokens("The Smith-chart, 2!"))
    }
}
