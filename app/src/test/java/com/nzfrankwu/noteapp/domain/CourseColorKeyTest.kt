package com.nzfrankwu.noteapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseColorKeyTest {
    private val size = 7

    @Test
    fun sameCode_writtenDifferently_getsTheSameSlot() {
        val slot = CourseColorKey.slot("ELECTENG 204", size)
        assertEquals(slot, CourseColorKey.slot("electeng204", size))
        assertEquals(slot, CourseColorKey.slot("  Electeng   204 ", size))
    }

    @Test
    fun slots_neverChange() {
        // Pinned values: if these fail, every course would silently change colour on the tablet.
        assertEquals(EXPECTED_ELECTENG_204, CourseColorKey.slot("ELECTENG 204", size))
        assertEquals(EXPECTED_COMPSYS_201, CourseColorKey.slot("COMPSYS 201", size))
    }

    @Test
    fun slot_isAlwaysInsideThePalette() {
        val codes = (100..999).map { "ENGGEN $it" } + listOf("", "Ä 1", "ELECTENG/COMPSYS 209")
        for (code in codes) {
            val slot = CourseColorKey.slot(code, size)
            assertTrue("$code → $slot", slot in 0 until size)
        }
    }

    @Test
    fun codes_spreadOverTheWholePalette() {
        val used = (100..999).map { CourseColorKey.slot("ENGGEN $it", size) }.groupingBy { it }.eachCount()
        assertEquals(size, used.size)
        // Each slot gets a fair share (900 codes / 7 ≈ 129 on average).
        assertTrue(used.toString(), used.values.all { it in 90..170 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyPalette_isRejected() {
        CourseColorKey.slot("ELECTENG 204", 0)
    }

    private companion object {
        const val EXPECTED_ELECTENG_204 = 4
        const val EXPECTED_COMPSYS_201 = 6
    }
}
