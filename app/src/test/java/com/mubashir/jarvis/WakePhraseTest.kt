package com.mubashir.jarvis

import com.mubashir.jarvis.voice.WakePhrase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseTest {

    @Test
    fun `the name on its own wakes it`() {
        assertTrue(WakePhrase.heard("jarvis"))
        assertTrue(WakePhrase.heard("Jarvis"))
        assertTrue(WakePhrase.heard("  jarvis  "))
    }

    @Test
    fun `the name inside a sentence wakes it`() {
        assertTrue(WakePhrase.heard("[unk] jarvis"))
        assertTrue(WakePhrase.heard("jarvis [unk] [unk]"))
        assertTrue(WakePhrase.heard("okay jarvis, torch on"))
    }

    @Test
    fun `punctuation around it does not hide it`() {
        assertTrue(WakePhrase.heard("jarvis."))
        assertTrue(WakePhrase.heard("\"jarvis\""))
    }

    @Test
    fun `nothing said is not a wake`() {
        assertFalse(WakePhrase.heard(null))
        assertFalse(WakePhrase.heard(""))
        assertFalse(WakePhrase.heard("   "))
        assertFalse(WakePhrase.heard("[unk]"))
        assertFalse(WakePhrase.heard("[unk] [unk] [unk]"))
    }

    @Test
    fun `a word that merely contains it does not wake it`() {
        // The expensive failure is a microphone that opens while you are
        // talking to somebody else, so this stays strict.
        assertFalse(WakePhrase.heard("jarvises"))
        assertFalse(WakePhrase.heard("jarvisy"))
        assertFalse(WakePhrase.heard("thejarvis"))
    }

    @Test
    fun `things that sound close are not it either`() {
        assertFalse(WakePhrase.heard("service"))
        assertFalse(WakePhrase.heard("jarvik"))
        assertFalse(WakePhrase.heard("harvest"))
    }

    @Test
    fun `the grammar names both the word and the catch-all`() {
        // Handed straight to the recogniser; if this is malformed it silently
        // falls back to recognising everything, which is the constant-waking
        // failure.
        assertTrue(WakePhrase.GRAMMAR.contains("\"jarvis\""))
        assertTrue(WakePhrase.GRAMMAR.contains("\"[unk]\""))
        assertTrue(WakePhrase.GRAMMAR.trim().startsWith("["))
        assertTrue(WakePhrase.GRAMMAR.trim().endsWith("]"))
    }
}
