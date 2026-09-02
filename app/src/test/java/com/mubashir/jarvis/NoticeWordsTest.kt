package com.mubashir.jarvis

import com.mubashir.jarvis.sense.Notice
import com.mubashir.jarvis.sense.NoticeWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeWordsTest {

    private val now = 1_700_000_000_000L
    private fun minutesAgo(n: Long) = now - n * 60_000

    private fun notice(
        app: String = "WhatsApp",
        title: String = "Ali",
        text: String = "Are you coming?",
        at: Long = minutesAgo(5),
        ongoing: Boolean = false,
        summary: Boolean = false,
    ) = Notice(app, title, text, at, ongoing, summary)

    @Test
    fun `an empty shade says so plainly`() {
        assertEquals("Nothing new.", NoticeWords.describe(emptyList(), now))
    }

    @Test
    fun `a message is read with the app it came from`() {
        val said = NoticeWords.describe(listOf(notice()), now)
        assertEquals("WhatsApp, Ali: Are you coming?.", said)
    }

    @Test
    fun `a permanent notification is not news`() {
        // A music player, a running download and a VPN each keep one of these
        // all day. They are what the phone is doing, not what happened.
        val said = NoticeWords.describe(
            listOf(notice(app = "Spotify", title = "Playing", ongoing = true)),
            now,
        )
        assertEquals("Nothing new.", said)
    }

    @Test
    fun `the header above a stack is not a notification`() {
        val said = NoticeWords.describe(
            listOf(notice(title = "3 new messages", summary = true)),
            now,
        )
        assertEquals("Nothing new.", said)
    }

    @Test
    fun `an app updating one notification in place is counted once`() {
        // A progress bar posts the same notification many times. Only the
        // newest is news; the rest are it changing its mind.
        val many = (1..20).map { notice(text = "Downloading $it%", at = minutesAgo(20 - it.toLong())) }
        assertEquals(1, NoticeWords.worthSaying(many, now).size)
    }

    @Test
    fun `different people in the same app are different news`() {
        val two = listOf(notice(title = "Ali"), notice(title = "Sara"))
        assertEquals(2, NoticeWords.worthSaying(two, now).size)
    }

    @Test
    fun `yesterday is not what I missed`() {
        val old = notice(at = minutesAgo(60 * 20))
        assertEquals("Nothing new.", NoticeWords.describe(listOf(old), now))
    }

    @Test
    fun `newest is said first`() {
        val said = NoticeWords.describe(
            listOf(
                notice(title = "Older", at = minutesAgo(60)),
                notice(title = "Newer", at = minutesAgo(2)),
            ),
            now,
        )
        assertTrue(said, said.indexOf("Newer") < said.indexOf("Older"))
    }

    @Test
    fun `a busy shade is summarised rather than recited`() {
        val many = (1..12).map { notice(title = "Person $it", at = minutesAgo(it.toLong())) }
        val said = NoticeWords.describe(many, now)
        assertTrue(said, said.contains("Person 1"))
        assertTrue(said, said.contains("Person 5"))
        assertTrue(said, !said.contains("Person 9"))
        assertTrue(said, said.contains("And 7 more"))
    }

    @Test
    fun `a title repeated as the text is not said twice`() {
        val said = NoticeWords.describe(
            listOf(notice(title = "Battery low", text = "Battery low")),
            now,
        )
        assertEquals("WhatsApp, Battery low.", said)
    }

    @Test
    fun `an empty notification is dropped rather than read as silence`() {
        assertEquals(
            "Nothing new.",
            NoticeWords.describe(listOf(notice(title = "  ", text = "")), now),
        )
    }
}
