package com.mubashir.jarvis.voice

import android.speech.SpeechRecognizer

/** What went wrong listening, and whether the user can do anything about it. */
data class VoiceProblem(
    val message: String,
    /** True when the fix is in system settings, so the UI can offer a way there. */
    val fixableInSettings: Boolean = false,
)

/**
 * Explains a SpeechRecognizer error code.
 *
 * Android 13 added codes 10–15 and the first version of this only went up to 8,
 * so the one that actually happens on a real phone — 13, the language pack is
 * not installed — came out as "code 13" and told the user nothing.
 */
fun describeVoiceError(code: Int): VoiceProblem = when (code) {
    SpeechRecognizer.ERROR_AUDIO ->
        VoiceProblem("No sound reached the microphone.")

    SpeechRecognizer.ERROR_CLIENT ->
        VoiceProblem("Recognizer band ho gaya")

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        VoiceProblem("Jarvis does not have permission to use the microphone.")

    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        VoiceProblem(
            "The recogniser asked for the internet, which means no offline language pack is installed.",
            fixableInSettings = true,
        )

    SpeechRecognizer.ERROR_NO_MATCH ->
        VoiceProblem("Did not catch that — say it again.")

    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
        VoiceProblem("The recogniser is already running.")

    SpeechRecognizer.ERROR_SERVER ->
        VoiceProblem("The speech service reported an error.")

    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
        VoiceProblem("Nothing was heard.")

    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
        VoiceProblem("The recogniser is busy — try again in a moment.")

    SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
        VoiceProblem("Lost contact with the speech service.")

    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
        VoiceProblem(
            "The recogniser does not support this language.",
            fixableInSettings = true,
        )

    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        VoiceProblem(
            "No offline language pack is installed for this language. " +
                "Add English in the phone's speech settings.",
            fixableInSettings = true,
        )

    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
        VoiceProblem("Could not check language support — try again.")

    SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS ->
        VoiceProblem("Could not read the language download status.")

    else -> VoiceProblem("Listening failed (code $code).")
}
