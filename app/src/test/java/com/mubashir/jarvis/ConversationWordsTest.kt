package com.mubashir.jarvis

import com.mubashir.jarvis.data.ConversationWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationWordsTest {

    // ---- naming ----

    @Test
    fun `a short first message is the whole title`() {
        assertEquals("Aaj kya schedule hai", ConversationWords.title("aaj kya schedule hai"))
    }

    @Test
    fun `a long one is cut at a word, never mid-word`() {
        val said = "remember that my brother is called Ali and he works at a bank in Karachi"
        val title = ConversationWords.title(said)
        assertTrue(title, title.endsWith("…"))
        assertTrue(title, title.length <= ConversationWords.TITLE_LIMIT + 2)
        // A title ending in "brot" reads as damage rather than abbreviation.
        assertFalse(title, title.contains("Ali an…"))
        assertTrue(title, title.dropLast(1).trim().split(' ').last().length > 1)
    }

    @Test
    fun `punctuation and stray whitespace do not survive into the list`() {
        assertEquals("Torch on", ConversationWords.title("  torch on!!  "))
        assertEquals("Torch on", ConversationWords.title("torch\n\n  on"))
    }

    @Test
    fun `an empty first message still gets a name`() {
        assertEquals("New conversation", ConversationWords.title(""))
        assertEquals("New conversation", ConversationWords.title("   "))
        assertEquals("New conversation", ConversationWords.title("..."))
    }

    // ---- finding ----

    @Test
    fun `every word has to appear, in any order`() {
        assertTrue(ConversationWords.matches("ali call", "Calling Ali about the review"))
        assertTrue(ConversationWords.matches("call ali", "Calling Ali about the review"))
        assertFalse(ConversationWords.matches("ali sara", "Calling Ali about the review"))
    }

    @Test
    fun `a half-typed word still finds things`() {
        // The box is used while still typing; waiting for whole words means the
        // list is empty exactly while someone is looking at it.
        assertTrue(ConversationWords.matches("sched", "Today's schedule"))
        assertTrue(ConversationWords.matches("SCHED", "today's schedule"))
    }

    @Test
    fun `an empty search finds nothing rather than everything`() {
        assertFalse(ConversationWords.matches("", "anything at all"))
        assertFalse(ConversationWords.matches("   ", "anything at all"))
    }

    @Test
    fun `a result shows the part that matched, not the first line`() {
        val long = "I have been thinking about this for a while and eventually " +
            "decided that Ali should handle the review, then we move on."
        val shown = ConversationWords.snippet(long, "ali")
        assertTrue(shown, shown.contains("Ali"))
        assertTrue(shown, shown.startsWith("…"))
        assertTrue(shown, shown.length < long.length)
    }

    @Test
    fun `a short message is shown whole, with no ellipsis`() {
        val shown = ConversationWords.snippet("Ali is my brother", "ali")
        assertEquals("Ali is my brother", shown)
    }

    @Test
    fun `a title match outranks a body match`() {
        // Someone searching for a conversation wants the conversation, not
        // every time the word was said inside one.
        val titleHit = ConversationWords.score("ali", "Calling Ali", "nothing here")
        val bodyHit = ConversationWords.score("ali", "Today's schedule", "I told Ali about it")
        assertTrue("$titleHit vs $bodyHit", titleHit > bodyHit)
    }

    @Test
    fun `the whole phrase outranks the same words scattered`() {
        val phrase = ConversationWords.score("landing page", "Landing page plan", "")
        val scattered = ConversationWords.score("landing page", "Page about the landing", "")
        assertTrue("$phrase vs $scattered", phrase > scattered)
    }

    @Test
    fun `nothing matching scores nothing`() {
        assertEquals(0, ConversationWords.score("", "Anything", "anything"))
        assertEquals(0, ConversationWords.score("zebra", "Calling Ali", "about the review"))
    }
}
