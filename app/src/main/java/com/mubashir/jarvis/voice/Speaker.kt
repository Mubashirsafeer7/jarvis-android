package com.mubashir.jarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Jarvis speaking, using the engine already on the phone.
 *
 * Android's TextToSpeech works offline once its voice data is installed, so it
 * costs nothing and adds no dependency. Replies are Hinglish written in Latin
 * script, which an Indian English voice reads far more naturally than a
 * US or UK one.
 */
class Speaker(context: Context) {

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Set when the engine has no usable voice, so the UI can say why it is silent. */
    var unavailableReason: String? = null
        private set

    private val tts = TextToSpeech(context.applicationContext) { status ->
        if (status != TextToSpeech.SUCCESS) {
            unavailableReason = "Phone par text-to-speech engine nahi mila"
            return@TextToSpeech
        }
        unavailableReason = pickVoice()
        _ready.value = unavailableReason == null
    }

    private fun pickVoice(): String? {
        // Indian English first, then anything English, before giving up.
        for (locale in listOf(Locale("en", "IN"), Locale.UK, Locale.US)) {
            when (tts.setLanguage(locale)) {
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_NOT_SUPPORTED -> continue

                else -> return null
            }
        }
        return "English voice install nahi hai — Settings me TTS voice data download karein"
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _speaking.value = false
            }

            @Deprecated("Required by the base class", ReplaceWith(""))
            override fun onError(utteranceId: String?) {
                _speaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _speaking.value = false
            }
        })
    }

    fun speak(text: String) {
        if (!_ready.value || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        tts.stop()
        _speaking.value = false
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        _speaking.value = false
        _ready.value = false
    }

    private companion object {
        const val UTTERANCE_ID = "jarvis"
    }
}
