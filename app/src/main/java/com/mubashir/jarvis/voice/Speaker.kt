package com.mubashir.jarvis.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Jarvis speaking, using the engine already on the phone.
 *
 * Android's TextToSpeech works offline once its voice data is installed, so it
 * costs nothing and adds no dependency. An Indian English voice reads the
 * assistant's English far more naturally than a US or UK one.
 */
class Speaker(context: Context) {

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Set when the engine has no usable voice, so the UI can say why it is silent. */
    @Volatile
    var unavailableReason: String? = null
        private set

    /** Distinct per utterance: one shared id made the speaking flag lie. */
    private val nextId = AtomicInteger(0)

    /** Utterances handed to the engine and not yet finished. */
    private val outstanding = AtomicInteger(0)

    /**
     * Said before the engine finished connecting. Initialisation is asynchronous
     * and the first sentence of a reply routinely beat it, so those sentences
     * were dropped in silence rather than spoken.
     */
    private val pending = ArrayDeque<Pair<String, Boolean>>()

    private val tts = TextToSpeech(context.applicationContext) { status ->
        if (status != TextToSpeech.SUCCESS) {
            unavailableReason = "No text-to-speech engine is installed on this phone."
            return@TextToSpeech
        }
        unavailableReason = pickVoice()
        _ready.value = unavailableReason == null
        if (_ready.value) drainPending()
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
        return "No English voice is installed. Add one in Settings, under text-to-speech."
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) = finished()

            @Deprecated("Required by the base class", ReplaceWith(""))
            override fun onError(utteranceId: String?) = finished()

            override fun onError(utteranceId: String?, errorCode: Int) = finished()
        })
    }

    /**
     * Only the last outstanding utterance ending means Jarvis has stopped
     * talking. Reporting it on the first one dropped the reactor out of its
     * speaking animation while the rest of the answer was still being read.
     */
    private fun finished() {
        if (outstanding.decrementAndGet() <= 0) {
            outstanding.set(0)
            _speaking.value = false
        }
    }

    private fun drainPending() {
        val queued = synchronized(pending) { pending.toList().also { pending.clear() } }
        queued.forEach { (text, interrupt) -> enqueue(text, interrupt) }
    }

    /**
     * @param interrupt true to cut off whatever is being said, false to queue
     * behind it. Answers are spoken sentence by sentence as they generate, so
     * only the first sentence of a reply interrupts.
     */
    fun speak(text: String, interrupt: Boolean = true) {
        if (text.isBlank()) return
        if (unavailableReason != null) return
        if (!_ready.value) {
            synchronized(pending) {
                if (interrupt) pending.clear()
                if (pending.size < MAX_PENDING) pending.addLast(text to interrupt)
            }
            return
        }
        enqueue(text, interrupt)
    }

    private fun enqueue(text: String, interrupt: Boolean) {
        if (interrupt) outstanding.set(0)
        // The engine refuses anything past its own limit, and used to do it
        // silently — a long answer simply lost its tail. Only the first chunk
        // may flush, or the rest of the sentence would cut off its own start.
        text.chunkedForSpeech().forEachIndexed { index, part ->
            val mode = if (interrupt && index == 0) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }
            outstanding.incrementAndGet()
            val result = tts.speak(part, mode, null, "jarvis-${nextId.incrementAndGet()}")
            if (result != TextToSpeech.SUCCESS) finished()
        }
    }

    fun stop() {
        synchronized(pending) { pending.clear() }
        tts.stop()
        outstanding.set(0)
        _speaking.value = false
    }

    fun shutdown() {
        stop()
        tts.shutdown()
        _ready.value = false
    }

    private fun String.chunkedForSpeech(): List<String> {
        val limit = maxLength
        return if (length <= limit) listOf(this) else chunked(limit)
    }

    private val maxLength: Int
        get() = runCatching { TextToSpeech.getMaxSpeechInputLength() }
            .getOrDefault(DEFAULT_MAX_LENGTH)
            .coerceAtLeast(1)

    private companion object {
        const val MAX_PENDING = 16
        const val DEFAULT_MAX_LENGTH = 4000
    }
}
