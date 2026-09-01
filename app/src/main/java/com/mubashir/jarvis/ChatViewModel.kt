package com.mubashir.jarvis

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.InferenceEngine
import com.mubashir.jarvis.data.StoredMessage
import com.mubashir.jarvis.model.DownloadState
import com.mubashir.jarvis.model.InstalledModel
import com.mubashir.jarvis.model.ModelSpec
import com.mubashir.jarvis.voice.MicLevel
import com.mubashir.jarvis.voice.SentenceSplitter
import com.mubashir.jarvis.voice.VoiceInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
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
    /**
     * What the busy overlay should say. It always claimed a model was loading,
     * including while exporting a 4 GB file or running a benchmark.
     */
    val busyMessage: Int = R.string.busy_loading,
    val generating: Boolean = false,
    val listening: Boolean = false,
    /** Smoothed microphone loudness, 0..1, driving the reactor while listening. */
    val micLevel: Float = 0f,
    /** True from the moment the mic opens until the spoken reply ends. */
    val voiceMode: Boolean = false,
    /** What the recogniser thinks it heard so far, shown while the user talks. */
    val heardSoFar: String = "",
    val speaking: Boolean = false,
    val speakReplies: Boolean = true,
    val voiceNote: String? = null,
    val benchmark: String? = null,
    /** Free space on the model volume. Sampled on refresh, never during layout. */
    val freeSpaceGb: Double = 0.0,
    val notice: String? = null,
    val error: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val runtime = (app as JarvisApplication).runtime
    private val models = runtime.models
    private val downloadStore = runtime.downloads
    private val engine = runtime.engine
    private val speaker = runtime.speaker
    private val voice = runtime.voice
    private val settings = runtime.settings
    private val chats = runtime.chats
    private val micLevel = MicLevel()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val engineState: StateFlow<InferenceEngine.State> = engine.state

    private var downloadId: Long? = null
    private var downloadWatch: Job? = null
    private var generation: Job? = null

    val capabilities = runtime.capabilities

    private var listening: Job? = null

    init {
        // The engine is process-scoped now, so a model loaded before this screen
        // existed is still loaded — reflect that rather than showing setup again.
        val restored = chats.load().map { ChatMessage(it.fromUser, it.text) }
        _ui.update {
            it.copy(
                loadedModel = engine.loadedModelPath?.let { path -> File(path).name },
                messages = restored,
                speakReplies = settings.settings.value.speakReplies,
            )
        }
        refreshInstalled()
        resumePendingDownload()
        viewModelScope.launch {
            speaker.speaking.collect { on -> _ui.update { it.copy(speaking = on) } }
        }
    }

    fun micAvailable(): Boolean = voice.isAvailable()

    fun hasMicPermission(): Boolean = voice.hasMicPermission()

    /** Why the phone cannot speak, if it cannot. Null when speech works. */
    fun speechUnavailableReason(): String? = speaker.unavailableReason

    fun toggleSpeakReplies() = setSpeakReplies(!_ui.value.speakReplies)

    fun setSpeakReplies(on: Boolean) {
        if (!on) speaker.stop()
        settings.setSpeakReplies(on)
        _ui.update { it.copy(speakReplies = on) }
    }

    fun setPredictLength(tokens: Int) = settings.setPredictLength(tokens)

    fun predictLength(): Int = settings.settings.value.predictLength

    /** Forgets the conversation, on the screen and on disk. */
    fun clearChat() {
        chats.clear()
        _ui.update { it.copy(messages = emptyList()) }
    }

    fun clearBenchmark() = _ui.update { it.copy(benchmark = null) }

    private fun persistChat() {
        chats.save(_ui.value.messages.map { StoredMessage(it.fromUser, it.text) })
    }

    /** Listens for one utterance and sends it as a prompt. */
    fun startListening() {
        if (_ui.value.listening || _ui.value.generating || _ui.value.busy) return
        speaker.stop()
        micLevel.reset()
        _ui.update {
            it.copy(
                listening = true, voiceMode = true, heardSoFar = "", micLevel = 0f,
                voiceNote = null, error = null,
            )
        }

        listening = viewModelScope.launch {
            try {
                voice.listen().collect { event ->
                    when (event) {
                        is VoiceInput.Event.Listening ->
                            _ui.update { it.copy(voiceNote = getApplication<Application>().getString(R.string.voice_listening)) }

                        is VoiceInput.Event.Partial ->
                            _ui.update { it.copy(heardSoFar = event.text) }

                        is VoiceInput.Event.Level -> {
                            val level = micLevel.update(event.rmsDb)
                            _ui.update { it.copy(micLevel = level) }
                        }

                        is VoiceInput.Event.Heard -> {
                            _ui.update {
                                it.copy(listening = false, heardSoFar = "", voiceNote = null)
                            }
                            send(event.text, fromVoice = true)
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
        micLevel.reset()
        _ui.update { it.copy(listening = false, heardSoFar = "", micLevel = 0f, voiceNote = null) }
    }

    fun stopSpeaking() = speaker.stop()

    /** Closes the reactor overlay and silences anything still being said. */
    fun exitVoiceMode() {
        speaker.stop()
        stopListening()
        _ui.update { it.copy(voiceMode = false) }
    }

    /**
     * Listing models stats every file and reads a header from each, and measuring
     * free space is a syscall — neither belongs on the thread drawing the screen.
     */
    fun refreshInstalled() {
        viewModelScope.launch {
            val (found, free) = withContext(Dispatchers.IO) {
                models.installed() to models.usableSpaceBytes() / BYTES_PER_GB
            }
            _ui.update { it.copy(installed = found, freeSpaceGb = free) }
        }
    }

    fun download(spec: ModelSpec) {
        if (_ui.value.downloadingSpec != null) return
        viewModelScope.launch {
            val free = withContext(Dispatchers.IO) { models.usableSpaceBytes() }
            if (free < spec.approxBytes) {
                _ui.update {
                    it.copy(
                        error = getApplication<Application>().getString(
                            R.string.error_needs_space, spec.approxBytes / BYTES_PER_GB,
                        ),
                    )
                }
                return@launch
            }
            // enqueue throws if the user has disabled the download manager, which
            // is common enough on custom ROMs to be worth surviving.
            val id = runCatching { models.startDownload(spec) }
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    _ui.update { it.copy(error = describeFailure(e)) }
                    return@launch
                }
            downloadStore.remember(id, spec.id)
            watchDownload(id, spec, autoLoad = true)
        }
    }

    /**
     * Picks up a download that was still running when the app was last closed.
     * Without this the setup screen offered to start it again from zero.
     */
    private fun resumePendingDownload() {
        val (id, spec) = downloadStore.pending() ?: return
        watchDownload(id, spec, autoLoad = false)
    }

    private fun watchDownload(id: Long, spec: ModelSpec, autoLoad: Boolean) {
        downloadWatch?.cancel()
        downloadId = id
        _ui.update { it.copy(downloadingSpec = spec, error = null) }
        downloadWatch = viewModelScope.launch {
            try {
                models.observeDownload(id, spec).collect { state ->
                    _ui.update { it.copy(download = state) }
                    when (state) {
                        is DownloadState.Done -> {
                            finishDownload()
                            refreshInstalled()
                            if (autoLoad) load(state.file)
                        }

                        is DownloadState.Failed -> {
                            finishDownload()
                            _ui.update { it.copy(error = state.reason) }
                        }

                        is DownloadState.Running, is DownloadState.Waiting -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Querying the download manager can fail outright. It used to take
                // the whole app down with it, mid-download.
                finishDownload()
                _ui.update { it.copy(error = describeFailure(e)) }
            }
        }
    }

    private fun finishDownload() {
        downloadId = null
        downloadStore.forget()
        _ui.update { it.copy(downloadingSpec = null, download = null) }
    }

    fun cancelDownload() {
        // Cancel the watcher first. Left running, it saw the row disappear half a
        // second later and reported the user's own cancel as a failure — and if a
        // new download had started by then, cleared that one's state instead.
        downloadWatch?.cancel()
        downloadWatch = null
        downloadId?.let(models::cancelDownload)
        finishDownload()
    }

    fun import(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, busyMessage = R.string.busy_importing) }
            // Copying gigabytes is not something to do on the main thread.
            withContext(Dispatchers.IO) { models.import(uri, fileName) }
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
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, benchmark = null, busyMessage = R.string.busy_loading) }
            runCatching { engine.load(model) }
                .onSuccess {
                    settings.setLastModelFile(model.name)
                    _ui.update { it.copy(busy = false, loadedModel = model.name) }
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
        if (_ui.value.busy) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { models.delete(model) }
            if (_ui.value.loadedModel == model.file.name) {
                runCatching { engine.unload() }
                _ui.update { it.copy(loadedModel = null, messages = emptyList()) }
            }
            // Refresh whatever delete reported. It returns false when the file
            // was already gone, and skipping the refresh then left a phantom row
            // in the list still offering to load it.
            refreshInstalled()
        }
    }

    fun send(text: String, fromVoice: Boolean = false) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _ui.value.generating || _ui.value.busy) return
        if (_ui.value.loadedModel == null) return
        if (!fromVoice) _ui.update { it.copy(voiceMode = false) }

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
            // Speak each sentence as it lands rather than waiting for the whole
            // answer — on a 3B model that wait was half a minute of silence.
            val sentences = SentenceSplitter()
            var spokenAnything = false
            try {
                engine.ask(prompt, settings.settings.value.predictLength).collect { token ->
                    reply.append(token)
                    _ui.update { s -> s.copy(messages = s.messages.replaceLast(reply.toString(), true)) }

                    if (_ui.value.speakReplies) {
                        sentences.accept(token).forEach { sentence ->
                            speaker.speak(sentence, interrupt = !spokenAnything)
                            spokenAnything = true
                        }
                    }
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
            if (_ui.value.speakReplies) {
                sentences.flush()?.let { tail ->
                    speaker.speak(tail, interrupt = !spokenAnything)
                }
            }
            persistChat()
        }
    }

    fun stopGenerating() {
        generation?.cancel()
        generation = null
        // Sentences already handed to the queue kept playing after Stop, so the
        // only way to shut Jarvis up was to turn speech off entirely.
        speaker.stop()
        _ui.update { s ->
            s.copy(
                messages = s.messages.map { it.copy(streaming = false) },
                generating = false,
            )
        }
        persistChat()
    }

    fun benchmark() {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, benchmark = null, error = null, busyMessage = R.string.busy_measuring) }
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

    /** Copies a model to Downloads so an uninstall cannot take it with it. */
    fun export(model: InstalledModel) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, notice = null, busyMessage = R.string.busy_saving) }
            val result = withContext(Dispatchers.IO) { models.exportToDownloads(model) }
            result
                .onSuccess { path ->
                    _ui.update {
                        it.copy(
                            busy = false,
                            notice = getApplication<Application>()
                                .getString(R.string.notice_saved_to, path),
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _ui.update { it.copy(busy = false, error = describeFailure(e)) }
                }
        }
    }

    fun dismissError() = _ui.update { it.copy(error = null, notice = null) }

    // Nothing is torn down here on purpose. The engine, speaker and recogniser
    // belong to the process, not to this screen: destroying the engine on the way
    // out left the native singleton dead for the rest of the process, and every
    // later model load failed until the app was force-stopped.

    private fun List<ChatMessage>.replaceLast(text: String, streaming: Boolean) =
        if (isEmpty()) this else dropLast(1) + last().copy(text = text, streaming = streaming)

    private companion object {
        const val BYTES_PER_GB = 1_073_741_824.0
    }
}
