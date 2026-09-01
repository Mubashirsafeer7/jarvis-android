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
        VoiceProblem("Microphone se awaaz nahi aayi")

    SpeechRecognizer.ERROR_CLIENT ->
        VoiceProblem("Recognizer band ho gaya")

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        VoiceProblem("Microphone ki ijazat nahi hai")

    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        VoiceProblem(
            "Recognizer ne internet maanga — offline language pack install nahi hai",
            fixableInSettings = true,
        )

    SpeechRecognizer.ERROR_NO_MATCH ->
        VoiceProblem("Samajh nahi aaya, dobara boliye")

    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
        VoiceProblem("Recognizer pehle se chal raha hai")

    SpeechRecognizer.ERROR_SERVER ->
        VoiceProblem("Recognizer service ne error diya")

    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
        VoiceProblem("Kuch sunai nahi diya")

    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
        VoiceProblem("Recognizer abhi masroof hai, thodi der baad")

    SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
        VoiceProblem("Recognizer service se raabta toot gaya")

    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
        VoiceProblem(
            "Yeh zubaan recognizer support nahi karta",
            fixableInSettings = true,
        )

    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        VoiceProblem(
            "Is zubaan ka offline pack phone par install nahi hai. " +
                "Settings mein jaakar English download karein.",
            fixableInSettings = true,
        )

    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
        VoiceProblem("Zubaan check nahi ho saki, dobara koshish karein")

    SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS ->
        VoiceProblem("Language download ka status nahi mil saka")

    else -> VoiceProblem("Sun-ne mein masla hua (code $code)")
}
