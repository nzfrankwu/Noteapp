package com.nzfrankwu.noteapp.ui.deleted

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class DeletedTextTest {
    private val zone = ZoneId.of("Pacific/Auckland")
    private val deleted = Instant.parse("2026-11-17T01:00:00Z") // 2 pm in Auckland

    @Test
    fun subtitle_saysWhenAndHowLong() {
        assertEquals("Deleted today · 30 days left", DeletedText.subtitle(deleted, deleted.plusSeconds(60), zone))
        assertEquals("Deleted yesterday · 29 days left", DeletedText.subtitle(deleted, deleted.plus(Duration.ofDays(1)), zone))
        assertEquals("Deleted 28 days ago · 2 days left", DeletedText.subtitle(deleted, deleted.plus(Duration.ofDays(28)), zone))
        assertEquals("Deleted 29 days ago · Less than a day left", DeletedText.subtitle(deleted, deleted.plus(Duration.ofDays(29)), zone))
        // 29 days and 13 hours later it's 3 am on the 30th calendar day in Auckland.
        assertEquals("Deleted 30 days ago · Less than a day left", DeletedText.subtitle(deleted, deleted.plus(Duration.ofDays(29)).plusSeconds(3600 * 13), zone))
        assertEquals("Deleted 31 days ago · Less than a day left", DeletedText.subtitle(deleted, deleted.plus(Duration.ofDays(31)), zone))
    }

    @Test
    fun untilGone() {
        assertEquals("It's deleted for good in 30 days.", DeletedText.untilGone(deleted, deleted))
        assertEquals("It's deleted for good in 30 days.", DeletedText.untilGone(deleted, deleted.plusSeconds(60)))
        assertEquals("It's deleted for good in 2 days.", DeletedText.untilGone(deleted, deleted.plus(Duration.ofDays(28))))
        assertEquals("It's deleted for good within a day.", DeletedText.untilGone(deleted, deleted.plus(Duration.ofDays(29)).plusSeconds(3600 * 13)))
    }
}
