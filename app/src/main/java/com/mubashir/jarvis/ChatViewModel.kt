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
    val benchmark: String? = null,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val models = ModelManager(app)
    private val engine = JarvisEngine(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val engineState: StateFlow<InferenceEngine.State> = engine.state

    private var downloadId: Long? = null
    private var generation: Job? = null

    val capabilities = DeviceCapabilities.read(app)

    init {
        refreshInstalled()
    }

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
            _ui.update { s ->
                s.copy(
                    messages = s.messages.replaceLast(reply.toString().ifBlank { "…" }, false),
                    generating = false,
                )
            }
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
        engine.destroy()
        super.onCleared()
    }

    private fun List<ChatMessage>.replaceLast(text: String, streaming: Boolean) =
        if (isEmpty()) this else dropLast(1) + last().copy(text = text, streaming = streaming)
}
