package com.mubashir.jarvis

import com.mubashir.jarvis.memory.MemoryNoticer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of memory that can embarrass Jarvis.
 *
 * A missed fact costs nothing — the owner can always say "remember this". A
 * false one is repeated back with confidence for months. So most of this file
 * is about what must *not* be remembered.
 */
class MemoryNoticerTest {

    private fun one(said: String): String {
        val found = MemoryNoticer.notice(said)
        assertEquals("expected exactly one fact from: $said", 1, found.size)
        return found.single()
    }

    private fun none(said: String) {
        assertEquals("should not have been remembered: $said", emptyList<String>(), MemoryNoticer.notice(said))
    }

    @Test
    fun `a name is picked up in either language`() {
        assertTrue(one("My name is Mubashir").contains("Mubashir"))
        assertTrue(one("mera naam Mubashir hai").contains("Mubashir"))
    }

    @Test
    fun `a relation is picked up in either language`() {
        assertTrue(one("My brother is called Ali").contains("Ali"))
        assertTrue(one("mera bhai Ali hai").contains("Ali"))
        assertTrue(one("meri behen ka naam Sara hai").contains("Sara"))
    }

    @Test
    fun `where someone lives and what they do`() {
        assertTrue(one("I live in Karachi").contains("Karachi"))
        assertTrue(one("mein Karachi mein rehta hu").contains("Karachi"))
        assertTrue(one("I work on VPS websites").contains("VPS", ignoreCase = true))
        assertTrue(one("I am a developer").contains("developer"))
    }

    @Test
    fun `likes and dislikes are opposites, not the same fact`() {
        assertTrue(one("I like short answers").contains("likes"))
        assertTrue(one("mujhe short answers pasand hai").contains("likes"))
        assertTrue(one("I don't like long replies").contains("does not like"))
        assertTrue(one("mujhe lambi baatein pasand nahi").contains("does not like"))
    }

    @Test
    fun `an english question is never a fact`() {
        none("what is my brother's name")
        none("who is my brother")
        none("tell me about my brother")
        none("is my name Mubashir")
        none("do I like short answers")
    }

    @Test
    fun `an urdu question is never a fact, even mid-sentence`() {
        // The one that got through. Urdu puts its question word in the middle,
        // so anchoring the check to the first word — which is right for
        // English — filed "who is my brother" as "the user's brother is Kaun".
        none("mera bhai kaun hai")
        none("kya mera naam yaad hai")
        none("meri behen kahan hai")
        none("mera kaam kya hai")
    }

    @Test
    fun `a question mark settles it whatever the words are`() {
        none("mera bhai Ali hai?")
        none("My brother is called Ali?")
    }

    @Test
    fun `ordinary talk is not a fact`() {
        none("hello")
        none("torch on")
        none("what is the weather")
        none("thanks")
        none("")
    }

    @Test
    fun `one sentence produces one fact, not the same fact twice`() {
        // "My name is Mubashir" fits both the name rule and the general
        // "my X is Y" rule. Storing both files one fact in two shapes, which
        // then read as two facts that disagree.
        assertEquals(1, MemoryNoticer.notice("My name is Mubashir").size)
        assertEquals(1, MemoryNoticer.notice("mera naam Mubashir hai").size)
    }

    @Test
    fun `a rambling sentence is left alone rather than half understood`() {
        // Better to remember nothing than to file the first clause and silently
        // drop the other three.
        none("My brother is called Ali and he lives in Lahore and works at a bank")
    }
}
