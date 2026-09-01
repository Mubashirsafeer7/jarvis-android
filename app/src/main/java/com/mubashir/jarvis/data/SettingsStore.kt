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
/** Where the thinking happens. */
enum class BrainChoice { Phone, Server }

data class Settings(
    val speakReplies: Boolean = true,
    /** Whether spoken commands may act on the phone rather than only be talked about. */
    val phoneControl: Boolean = true,
    /** Keep a copy of each downloaded model in Downloads, so an uninstall cannot lose it. */
    val keepRescueCopy: Boolean = true,
    val predictLength: Int = DEFAULT_PREDICT,
    val lastModelFile: String? = null,
    val brain: BrainChoice = BrainChoice.Phone,
    /** Base URL of a server speaking the OpenAI chat format, e.g. http://100.x.y.z:8080 */
    val serverUrl: String = "",
    val serverModel: String = "",
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
        phoneControl = prefs.getBoolean(KEY_CONTROL, true),
        keepRescueCopy = prefs.getBoolean(KEY_RESCUE, true),
        predictLength = prefs.getInt(KEY_PREDICT, Settings.DEFAULT_PREDICT),
        lastModelFile = prefs.getString(KEY_MODEL, null),
        brain = runCatching {
            BrainChoice.valueOf(prefs.getString(KEY_BRAIN, null) ?: BrainChoice.Phone.name)
        }.getOrDefault(BrainChoice.Phone),
        serverUrl = prefs.getString(KEY_SERVER_URL, "").orEmpty(),
        serverModel = prefs.getString(KEY_SERVER_MODEL, "").orEmpty(),
    )

    fun setSpeakReplies(on: Boolean) = update { it.copy(speakReplies = on) }

    fun setPredictLength(tokens: Int) = update { it.copy(predictLength = tokens) }

    fun setPhoneControl(on: Boolean) = update { it.copy(phoneControl = on) }

    fun setKeepRescueCopy(on: Boolean) = update { it.copy(keepRescueCopy = on) }

    fun setBrain(choice: BrainChoice) = update { it.copy(brain = choice) }

    fun setServerUrl(url: String) = update { it.copy(serverUrl = url.trim()) }

    fun setServerModel(model: String) = update { it.copy(serverModel = model.trim()) }

    fun setLastModelFile(fileName: String?) = update { it.copy(lastModelFile = fileName) }

    private fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_SPEAK, next.speakReplies)
            .putBoolean(KEY_CONTROL, next.phoneControl)
            .putBoolean(KEY_RESCUE, next.keepRescueCopy)
            .putInt(KEY_PREDICT, next.predictLength)
            .putString(KEY_MODEL, next.lastModelFile)
            .putString(KEY_BRAIN, next.brain.name)
            .putString(KEY_SERVER_URL, next.serverUrl)
            .putString(KEY_SERVER_MODEL, next.serverModel)
            .apply()
    }

    private companion object {
        const val NAME = "settings"
        const val KEY_SPEAK = "speak_replies"
        const val KEY_CONTROL = "phone_control"
        const val KEY_RESCUE = "keep_rescue_copy"
        const val KEY_PREDICT = "predict_length"
        const val KEY_MODEL = "last_model"
        const val KEY_BRAIN = "brain"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SERVER_MODEL = "server_model"
    }
}
