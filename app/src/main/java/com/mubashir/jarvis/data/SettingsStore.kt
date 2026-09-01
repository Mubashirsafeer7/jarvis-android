package com.mubashir.jarvis.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the user has chosen, kept across restarts.
 *
 * Speech was on again every launch however many times it was turned off, because
 * nothing but a running download was ever written down.
 *
 * SharedPreferences rather than DataStore: it is already in use here, it needs no
 * dependency that cannot be resolved from where this project is built, and the
 * whole of it is three values.
 */
data class Settings(
    val speakReplies: Boolean = true,
    val predictLength: Int = DEFAULT_PREDICT,
    val lastModelFile: String? = null,
) {
    companion object {
        const val DEFAULT_PREDICT = 1024
        val PREDICT_CHOICES = listOf(512, 1024, 2048, 4096)
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private fun read() = Settings(
        speakReplies = prefs.getBoolean(KEY_SPEAK, true),
        predictLength = prefs.getInt(KEY_PREDICT, Settings.DEFAULT_PREDICT),
        lastModelFile = prefs.getString(KEY_MODEL, null),
    )

    fun setSpeakReplies(on: Boolean) = update { it.copy(speakReplies = on) }

    fun setPredictLength(tokens: Int) = update { it.copy(predictLength = tokens) }

    fun setLastModelFile(fileName: String?) = update { it.copy(lastModelFile = fileName) }

    private fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_SPEAK, next.speakReplies)
            .putInt(KEY_PREDICT, next.predictLength)
            .putString(KEY_MODEL, next.lastModelFile)
            .apply()
    }

    private companion object {
        const val NAME = "settings"
        const val KEY_SPEAK = "speak_replies"
        const val KEY_PREDICT = "predict_length"
        const val KEY_MODEL = "last_model"
    }
}
