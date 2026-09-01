package com.mubashir.jarvis

import com.mubashir.jarvis.tools.AppMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppMatcherTest {

    private val installed = listOf(
        "WhatsApp", "YouTube", "YouTube Music", "Chrome", "Gmail",
        "Phone", "Messages", "Camera", "Settings", "Google Play Store",
    )

    private fun match(said: String) = AppMatcher.bestMatch(said, installed)

    @Test
    fun `an exact name matches`() {
        assertEquals("WhatsApp", match("whatsapp"))
        assertEquals("Chrome", match("Chrome"))
    }

    @Test
    fun `the spaces speech recognition invents do not matter`() {
        // These are what the recogniser actually returns.
        assertEquals("WhatsApp", match("whats app"))
        assertEquals("YouTube", match("you tube"))
    }

    @Test
    fun `a shorter name wins over a longer one that also starts with it`() {
        // "youtube" must open YouTube, not YouTube Music.
        assertEquals("YouTube", match("youtube"))
    }

    @Test
    fun `the longer name is still reachable by saying it`() {
        assertEquals("YouTube Music", match("youtube music"))
    }

    @Test
    fun `nothing recognisable matches nothing`() {
        assertNull(match("spotify"))
        assertNull(match(""))
        assertNull(match("   "))
    }

    @Test
    fun `a tie is a question, not a guess`() {
        // Opening the wrong app is worse than admitting the ambiguity.
        assertNull(AppMatcher.bestMatch("mail", listOf("Mail", "mail")))
    }

    @Test
    fun `a short fragment does not match a long name by accident`() {
        // "cam" inside "Camera" is a coincidence, not an intention.
        assertNull(AppMatcher.bestMatch("era", installed))
    }
}
