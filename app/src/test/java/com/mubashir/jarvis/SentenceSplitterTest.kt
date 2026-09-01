package com.mubashir.jarvis

import com.mubashir.jarvis.voice.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    /** Feeds text the way generation does — a few characters at a time. */
    private fun stream(text: String, chunk: Int = 3): Pair<List<String>, String?> {
        val splitter = SentenceSplitter()
        val spoken = mutableListOf<String>()
        text.chunked(chunk).forEach { spoken += splitter.accept(it) }
        return spoken to splitter.flush()
    }

    @Test
    fun `speaking starts before the answer is finished`() {
        val splitter = SentenceSplitter()
        val first = splitter.accept("The reactor is online and stable. ")
        assertEquals(listOf("The reactor is online and stable."), first)
    }

    @Test
    fun `sentences arrive in order across chunk boundaries`() {
        val (spoken, left) = stream("First one here. Second one here. Third one here.")
        assertEquals(listOf("First one here.", "Second one here."), spoken)
        assertEquals("Third one here.", left)
    }

    @Test
    fun `a decimal point does not split a sentence`() {
        val (spoken, left) = stream("The file is 3.5 GB in total size. ")
        assertEquals(listOf("The file is 3.5 GB in total size."), spoken)
        assertNull(left)
    }

    @Test
    fun `questions and exclamations end sentences`() {
        val (spoken, _) = stream("How can I help you today? Right away sir! ")
        assertEquals(listOf("How can I help you today?", "Right away sir!"), spoken)
    }

    @Test
    fun `a devanagari full stop ends a sentence`() {
        val (spoken, _) = stream("यह पहला वाक्य है। दूसरा वाक्य। ")
        assertTrue(spoken.isNotEmpty())
        assertTrue(spoken.first().endsWith("।"))
    }

    @Test
    fun `a very short fragment waits for more`() {
        val splitter = SentenceSplitter()
        // "Hi." on its own is too short to be worth an utterance of its own.
        assertEquals(emptyList<String>(), splitter.accept("Hi. "))
        assertEquals("Hi.", splitter.flush())
    }

    @Test
    fun `an unterminated answer still gets spoken at the end`() {
        val (spoken, left) = stream("This answer never got a full stop")
        assertEquals(emptyList<String>(), spoken)
        assertEquals("This answer never got a full stop", left)
    }

    @Test
    fun `flush empties the buffer`() {
        val splitter = SentenceSplitter()
        splitter.accept("Something left over")
        assertEquals("Something left over", splitter.flush())
        assertNull(splitter.flush())
    }

    @Test
    fun `nothing in means nothing out`() {
        val splitter = SentenceSplitter()
        assertEquals(emptyList<String>(), splitter.accept(""))
        assertNull(splitter.flush())
    }
}
