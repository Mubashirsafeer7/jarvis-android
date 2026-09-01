package com.mubashir.jarvis

import com.arm.aichat.UnsupportedArchitectureException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ErrorTextTest {

    @Test
    fun `an exception with no message names itself`() {
        // The failure that cost a round of debugging: this one carries no
        // message, so a plain `e.message ?: fallback` said nothing useful.
        val text = describeFailure(UnsupportedArchitectureException())
        assertTrue(text, text.contains("CPU backend"))
        assertFalse("must not be a bare fallback", text.trim().isEmpty())
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
            assertTrue("empty for ${e.javaClass.simpleName}", text.trim().length > 5)
        }
    }
}
