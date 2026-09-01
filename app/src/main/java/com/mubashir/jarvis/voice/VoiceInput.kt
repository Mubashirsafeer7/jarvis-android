package com.mubashir.jarvis.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Jarvis listening, using the recogniser already on the phone.
 *
 * Android 13 added an on-device recogniser that needs no network, which is the
 * whole point here — so prefer it, and fall back to asking the general
 * recogniser for an offline result on older phones.
 */
class VoiceInput(private val context: Context) {

    sealed interface Event {
        /** The microphone is open; safe to prompt the user to speak. */
        data object Listening : Event

        /** Best guess so far, updated as the user talks. */
        data class Partial(val text: String) : Event

        data class Heard(val text: String) : Event

        /** Live microphone loudness in dB, for driving the reactor. */
        data class Level(val rmsDb: Float) : Event
        data class Failed(val reason: String, val fixableInSettings: Boolean = false) : Event
    }

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isAvailable(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> true

        else -> SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Opens the microphone and emits what it hears, ending after one utterance.
     * The recogniser must be created and driven from the main thread.
     */
    fun listen(): Flow<Event> = callbackFlow {
        if (!hasMicPermission()) {
            trySend(Event.Failed("Jarvis does not have permission to use the microphone."))
            close()
            return@callbackFlow
        }
        if (!isAvailable()) {
            trySend(Event.Failed("This phone has no speech recognition available."))
            close()
            return@callbackFlow
        }

        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        val recognizer =
            if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(Event.Listening)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstResult()?.let { trySend(Event.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val heard = results.firstResult()
                if (heard.isNullOrBlank()) {
                    trySend(Event.Failed("Nothing was heard."))
                } else {
                    trySend(Event.Heard(heard))
                }
                close()
            }

            override fun onError(error: Int) {
                val problem = describeVoiceError(error)
                trySend(Event.Failed(problem.message, problem.fixableInSettings))
                close()
            }

            override fun onRmsChanged(rmsdB: Float) {
                trySend(Event.Level(rmsdB))
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            // Ask for Indian English but do not demand it. Forcing EXTRA_LANGUAGE
            // to a pack the phone has not downloaded fails outright with
            // ERROR_LANGUAGE_UNAVAILABLE, which is what broke voice entirely;
            // as a preference the recogniser falls back to whatever it does have.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Only meaningful on the fallback path; the on-device recogniser is
            // offline by definition.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main)

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf(String::isNotBlank)

}
