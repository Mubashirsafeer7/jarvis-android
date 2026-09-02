package com.mubashir.jarvis

import com.mubashir.jarvis.memory.Fact
import com.mubashir.jarvis.memory.FactSource
import com.mubashir.jarvis.memory.MemoryRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRulesTest {

    private fun fact(
        text: String,
        source: FactSource = FactSource.Told,
        at: Long = 1_000L,
        pinned: Boolean = false,
    ) = Fact(
        id = text.hashCode().toLong(),
        text = text,
        topic = MemoryRules.topicOf(text),
        source = source,
        createdAt = at,
        pinned = pinned,
    )

    @Test
    fun `the words that matter survive and the scaffolding does not`() {
        val words = MemoryRules.keywordsOf("My brother is called Ali")
        assertTrue("brother" in words)
        assertTrue("called" in words)
        assertTrue("ali" in words)
        assertFalse("my" in words)
        assertFalse("is" in words)
    }

    @Test
    fun `hinglish scaffolding is stripped too`() {
        // The owner writes both languages in one sentence, so a stopword list
        // that only knows English leaves "ka", "ki", "hai" scoring as subject
        // matter and every fact looking related to every other one.
        val words = MemoryRules.keywordsOf("Mera bhai ka naam Ali hai")
        assertTrue("bhai" in words)
        assertTrue("ali" in words)
        assertFalse("mera" in words)
        assertFalse("naam" in words)
        assertFalse("hai" in words)
    }

    @Test
    fun `a question finds the fact that answers it`() {
        val facts = listOf(
            fact("Mubashir's brother is called Ali"),
            fact("Mubashir works on VPS websites"),
            fact("Mubashir prefers short answers"),
        )
        val found = MemoryRules.relevant("what is my brother's name", facts, limit = 1)
        assertEquals("Mubashir's brother is called Ali", found.single().text)
    }

    @Test
    fun `a pinned fact is never crowded out`() {
        val facts = listOf(fact("Mubashir prefers short answers", pinned = true)) +
            (1..20).map { fact("Unrelated fact number $it", at = 2_000L + it) }
        val found = MemoryRules.relevant("what is the weather", facts)
        assertTrue(found.any { it.pinned })
    }

    @Test
    fun `what the user said outranks what jarvis worked out`() {
        val told = fact("Mubashir's brother is called Ali", FactSource.Told, at = 1L)
        val noticed = fact("Mubashir mentioned someone called Bilal", FactSource.Noticed, at = 9L)
        val found = MemoryRules.relevant("hello", listOf(noticed, told), limit = 1)
        assertEquals(told.text, found.single().text)
    }

    @Test
    fun `only a handful of facts ever travel with a prompt`() {
        val many = (1..50).map { fact("Fact number $it", at = it.toLong()) }
        val found = MemoryRules.relevant("anything", many)
        assertEquals(MemoryRules.MAX_FACTS_IN_PROMPT, found.size)
    }

    @Test
    fun `a newer fact about the same thing replaces the older one`() {
        // Otherwise the model is handed two answers to one question and picks
        // whichever it likes, which reads as Jarvis not listening.
        val old = fact("Mubashir's brother is called Ali")
        val new = fact("Mubashir's brother is called Bilal")
        assertTrue(MemoryRules.replaces(new, old))
    }

    @Test
    fun `facts about different things live side by side`() {
        val brother = fact("Mubashir's brother is called Ali")
        val work = fact("Mubashir builds VPS websites")
        assertFalse(MemoryRules.replaces(work, brother))
        assertFalse(MemoryRules.replaces(brother, work))
    }

    @Test
    fun `saying the same thing twice does not store it twice`() {
        val once = fact("Mubashir prefers short answers")
        val again = fact("mubashir prefers SHORT answers")
        assertTrue(MemoryRules.replaces(again, once))
    }

    @Test
    fun `knowing nothing adds nothing to a prompt`() {
        assertNull(MemoryRules.contextBlock(emptyList()))
        assertEquals("hello", MemoryRules.withContext("hello", emptyList()))
    }

    @Test
    fun `what is known rides along with the question`() {
        val facts = listOf(fact("Mubashir's brother is called Ali"))
        val sent = MemoryRules.withContext("who is my brother", facts)
        assertTrue(sent.contains("Ali"))
        // The question itself has to survive intact, at the end, or the model
        // answers the context instead of the person.
        assertTrue(sent.trimEnd().endsWith("who is my brother"))
    }

    @Test
    fun `a fact with nothing in it is not a topic`() {
        assertEquals("", MemoryRules.topicOf("is the a"))
        assertEquals("", MemoryRules.topicOf(""))
    }
}
