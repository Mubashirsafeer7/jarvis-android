package com.mubashir.jarvis

import com.arm.aichat.UnsupportedArchitectureException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ErrorTextTest {

    @Test
    fun `an exception with no message still explains itself`() {
        // The failure that cost a round of debugging: this one carries no
        // message, so a plain `e.message ?: fallback` said nothing useful.
        //
        // This used to assert the words "CPU backend". That was wrong: the
        // native layer returns the same code for a missing backend, a model too
        // big for the phone's memory, and a damaged file — so naming one cause
        // told the user the wrong thing on what is in practice the likeliest
        // one. The requirement is an explanation and a way forward, not a
        // particular diagnosis.
        val text = describeFailure(UnsupportedArchitectureException())
        assertFalse("must not be a bare fallback", text.isBlank())
        assertTrue(text, text.contains("memory"))
        assertTrue("should suggest something to do", text.length > 40)
    }

    @Test
    fun `an unknown exception with no message still names its type`() {
        val text = describeFailure(IllegalStateException())
        assertTrue(text, text.contains("IllegalStateException"))
    }

    @Test
    fun `a blank message is treated as no message`() {
        val text = describeFailure(IOException("   "))
        assertTrue(text, text.contains("IOException"))
    }

    @Test
    fun `a real message is passed through`() {
        val text = describeFailure(IOException("File not found"))
        assertTrue(text, text.contains("File not found"))
    }

    @Test
    fun `every failure produces something readable`() {
        val failures = listOf(
            UnsupportedArchitectureException(),
            IllegalStateException(),
            IOException("   "),
            IOException("disk full"),
            RuntimeException("boom"),
            OutOfMemoryError(),
        )
        failures.forEach { e ->
            val text = describeFailure(e)
            // Non-blank is the actual requirement. A length floor rejected
            // RuntimeException("boom"), which is a perfectly good message.
            assertTrue("blank for ${e.javaClass.simpleName}", text.isNotBlank())
        }
    }
}
