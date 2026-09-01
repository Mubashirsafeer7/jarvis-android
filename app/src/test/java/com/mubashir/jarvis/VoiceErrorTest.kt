package com.mubashir.jarvis

import android.speech.SpeechRecognizer
import com.mubashir.jarvis.voice.describeVoiceError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceErrorTest {

    @Test
    fun `the error that actually broke voice explains itself`() {
        // Code 13 is what the phone reported. The first version stopped at code
        // 8, so this came out as "code 13" and told the user nothing.
        val problem = describeVoiceError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertTrue(problem.message, problem.message.contains("pack"))
        assertTrue("user should be sent to settings", problem.fixableInSettings)
        assertFalse("must not fall through to the raw code", problem.message.contains("code"))
    }

    @Test
    fun `every Android 13 code is covered`() {
        val newCodes = listOf(
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
            SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS,
        )
        newCodes.forEach { code ->
            val message = describeVoiceError(code).message
            assertFalse("code $code fell through: $message", message.contains("code $code"))
        }
    }

    @Test
    fun `every older code is covered too`() {
        (1..9).forEach { code ->
            val message = describeVoiceError(code).message
            assertFalse("code $code fell through: $message", message.contains("code $code"))
        }
    }

    @Test
    fun `only the settings-fixable ones offer settings`() {
        assertTrue(describeVoiceError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE).fixableInSettings)
        assertTrue(describeVoiceError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED).fixableInSettings)
        assertTrue(describeVoiceError(SpeechRecognizer.ERROR_NETWORK).fixableInSettings)

        assertFalse(describeVoiceError(SpeechRecognizer.ERROR_NO_MATCH).fixableInSettings)
        assertFalse(describeVoiceError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT).fixableInSettings)
    }

    @Test
    fun `an unknown code still says something and names itself`() {
        val message = describeVoiceError(999).message
        assertTrue(message, message.contains("999"))
        assertTrue(message.isNotBlank())
    }
}
