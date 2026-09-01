package com.mubashir.jarvis

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.InferenceEngine
import com.mubashir.jarvis.llm.JarvisEngine
import com.mubashir.jarvis.model.DownloadState
import com.mubashir.jarvis.model.InstalledModel
import com.mubashir.jarvis.model.ModelManager
import com.mubashir.jarvis.model.ModelSpec
import com.mubashir.jarvis.voice.Speaker
import com.mubashir.jarvis.voice.VoiceInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val streaming: Boolean = false,
)

data class UiState(
    val installed: List<InstalledModel> = emptyList(),
    val loadedModel: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val download: DownloadState? = null,
    val downloadingSpec: ModelSpec? = null,
    val busy: Boolean = false,
    val generating: Boolean = false,
    val listening: Boolean = false,
    /** What the recogniser thinks it heard so far, shown while the user talks. */
    val heardSoFar: String = "",
    val speaking: Boolean = false,
    val speakReplies: Boolean = true,
    val voiceNote: String? = null,
    val benchmark: String? = null,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val models = ModelManager(app)
    private val engine = JarvisEngine(app)
    private val speaker = Speaker(app)
    private val voice = VoiceInput(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val engineState: StateFlow<InferenceEngine.State> = engine.state

    private var downloadId: Long? = null
    private var generation: Job? = null

    val capabilities = DeviceCapabilities.read(app)

    private var listening: Job? = null

    init {
        refreshInstalled()
        viewModelScope.launch {
            speaker.speaking.collect { on -> _ui.update { it.copy(speaking = on) } }
        }
    }

    fun micAvailable(): Boolean = voice.isAvailable()

    fun hasMicPermission(): Boolean = voice.hasMicPermission()

    fun toggleSpeakReplies() {
        val on = !_ui.value.speakReplies
        if (!on) speaker.stop()
        _ui.update { it.copy(speakReplies = on) }
    }

    /** Listens for one utterance and sends it as a prompt. */
    fun startListening() {
        if (_ui.value.listening || _ui.value.generating || _ui.value.busy) return
        speaker.stop()
        _ui.update { it.copy(listening = true, heardSoFar = "", voiceNote = null, error = null) }

        listening = viewModelScope.launch {
            try {
                voice.listen().collect { event ->
                    when (event) {
                        is VoiceInput.Event.Listening ->
                            _ui.update { it.copy(voiceNote = "Sun raha hoon…") }

                        is VoiceInput.Event.Partial ->
                            _ui.update { it.copy(heardSoFar = event.text) }

                        is VoiceInput.Event.Heard -> {
                            _ui.update {
                                it.copy(listening = false, heardSoFar = "", voiceNote = null)
                            }
                            send(event.text)
                        }

                        is VoiceInput.Event.Failed ->
                            _ui.update {
                                it.copy(listening = false, heardSoFar = "", voiceNote = event.reason)
                            }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.update { it.copy(listening = false, error = describeFailure(e)) }
            }
            _ui.update { it.copy(listening = false) }
        }
    }

    fun stopListening() {
        listening?.cancel()
        listening = null
        _ui.update { it.copy(listening = false, heardSoFar = "", voiceNote = null) }
    }

    fun stopSpeaking() = speaker.stop()

    fun refreshInstalled() {
        _ui.update { it.copy(installed = models.installed()) }
    }

    fun usableSpaceGb(): Double = models.usableSpaceBytes() / 1_073_741_824.0

    fun download(spec: ModelSpec) {
        if (_ui.value.downloadingSpec != null) return
        if (models.usableSpaceBytes() < spec.approxBytes) {
            _ui.update { it.copy(error = "Storage kam hai — %.1f GB chahiye".format(spec.approxBytes / 1_073_741_824.0)) }
            return
        }
        val id = models.startDownload(spec)
        downloadId = id
        _ui.update { it.copy(downloadingSpec = spec, error = null) }
        viewModelScope.launch {
            models.observeDownload(id, spec).collect { state ->
                _ui.update { it.copy(download = state) }
                when (state) {
                    is DownloadState.Done -> {
                        downloadId = null
                        _ui.update { it.copy(downloadingSpec = null, download = null) }
                        refreshInstalled()
                        load(state.file)
                    }

                    is DownloadState.Failed -> {
                        downloadId = null
                        _ui.update {
                            it.copy(downloadingSpec = null, download = null, error = state.reason)
                        }
                    }

                    is DownloadState.Running -> Unit
                }
            }
        }
    }

    fun cancelDownload() {
        downloadId?.let(models::cancelDownload)
        downloadId = null
        _ui.update { it.copy(downloadingSpec = null, download = null) }
    }

    fun import(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            models.import(uri, fileName)
                .onSuccess { file ->
                    refreshInstalled()
                    load(file)
                }
                .onFailure { e ->
                    // runCatching swallows cancellation; rethrow so a cleared
                    // ViewModel does not look like a failure to the user.
                    if (e is CancellationException) throw e
                    _ui.update { it.copy(busy = false, error = describeFailure(e)) }
                }
        }
    }

    fun load(model: File) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, benchmark = null) }
            runCatching { engine.load(model) }
                .onSuccess {
                    _ui.update {
                        it.copy(busy = false, loadedModel = model.name, messages = emptyList())
                    }
                }
                .onFailure { e ->
                    // runCatching swallows cancellation; rethrow so a cleared
                    // ViewModel does not look like a failure to the user.
                    if (e is CancellationException) throw e
                    _ui.update { it.copy(busy = false, error = describeFailure(e)) }
                }
        }
    }

    fun delete(model: InstalledModel) {
        if (models.delete(model)) {
            if (_ui.value.loadedModel == model.file.name) {
                engine.unload()
                _ui.update { it.copy(loadedModel = null, messages = emptyList()) }
            }
            refreshInstalled()
        }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _ui.value.generating) return

        _ui.update {
            it.copy(
                messages = it.messages + ChatMessage(true, prompt) +
                    ChatMessage(false, "", streaming = true),
                generating = true,
                error = null,
            )
        }

        generation = viewModelScope.launch {
            val reply = StringBuilder()
            try {
                engine.ask(prompt).collect { token ->
                    reply.append(token)
                    _ui.update { s -> s.copy(messages = s.messages.replaceLast(reply.toString(), true)) }
                }
            } catch (e: CancellationException) {
                // Stop is a choice, not a failure. runCatching would have caught
                // this too and shown the user an error dialog for their own tap.
                throw e
            } catch (e: Throwable) {
                _ui.update { it.copy(error = describeFailure(e)) }
            }
            val answer = reply.toString()
            _ui.update { s ->
                s.copy(
                    messages = s.messages.replaceLast(answer.ifBlank { "…" }, false),
                    generating = false,
                )
            }
            if (_ui.value.speakReplies && answer.isNotBlank()) speaker.speak(answer)
        }
    }

    fun stopGenerating() {
        generation?.cancel()
        generation = null
        _ui.update { s ->
            s.copy(
                messages = s.messages.map { it.copy(streaming = false) },
                generating = false,
            )
        }
    }

    fun benchmark() {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, benchmark = null, error = null) }
            runCatching { engine.benchmark() }
                .onSuccess { result -> _ui.update { it.copy(busy = false, benchmark = result) } }
                .onFailure { e ->
                    // runCatching swallows cancellation; rethrow so a cleared
                    // ViewModel does not look like a failure to the user.
                    if (e is CancellationException) throw e
                    _ui.update { it.copy(busy = false, error = describeFailure(e)) }
                }
        }
    }

    fun dismissError() = _ui.update { it.copy(error = null) }

    override fun onCleared() {
        speaker.shutdown()
        engine.destroy()
        super.onCleared()
    }

    private fun List<ChatMessage>.replaceLast(text: String, streaming: Boolean) =
        if (isEmpty()) this else dropLast(1) + last().copy(text = text, streaming = streaming)
}
