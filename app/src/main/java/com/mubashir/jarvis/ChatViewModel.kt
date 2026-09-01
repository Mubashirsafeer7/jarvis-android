package com.mubashir.jarvis

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arm.aichat.InferenceEngine
import com.mubashir.jarvis.data.StoredMessage
import com.mubashir.jarvis.update.AvailableUpdate
import com.mubashir.jarvis.model.DownloadState
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.mubashir.jarvis.tools.Action
import com.mubashir.jarvis.tools.Ask
import com.mubashir.jarvis.tools.Command
import com.mubashir.jarvis.tools.Contact
import com.mubashir.jarvis.tools.ContactMatch
import com.mubashir.jarvis.tools.ContactMatcher
import com.mubashir.jarvis.tools.IntentRouter
import com.mubashir.jarvis.tools.ToolOutcome
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

/** Where a check for a newer build has got to. */
sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val update: AvailableUpdate) : UpdateUi
    data class Downloading(val downloaded: Long, val total: Long) : UpdateUi
    data class Ready(val file: File) : UpdateUi
    data class Failed(val reason: String) : UpdateUi
}

data class UiState(
    val installed: List<InstalledModel> = emptyList(),
    val loadedModel: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val download: DownloadState? = null,
    val downloadingSpec: ModelSpec? = null,
    val busy: Boolean = false,
    /**
     * Loading the model the app had last time, on its way up. Distinct from
     * [busy]: nothing the user did is waiting on it, so it gets a screen of its
     * own rather than a modal spinner over the setup list.
     */
    val startingUp: Boolean = false,
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
    val update: UpdateUi = UpdateUi.Idle,
    /** Something the app needs from the user before it can act. */
    val ask: Ask? = null,
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
        reloadLastModel()
        viewModelScope.launch {
            speaker.speaking.collect { on -> _ui.update { it.copy(speaking = on) } }
        }
    }

    /**
     * Picks the model back up on a cold start.
     *
     * Without this the app opened on the model picker every single time, because
     * a loaded model was only ever remembered in a process that Android had by
     * then killed — and a screen offering to download a model you already have
     * reads as if the download never worked.
     */
    private fun reloadLastModel() {
        if (engine.loadedModelPath != null) return
        val remembered = settings.settings.value.lastModelFile ?: return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                File(models.modelsDir, remembered).takeIf { it.isFile && models.isGguf(it) }
            }
            if (file == null) {
                // Deleted, or on a volume that is not mounted. Forget it rather
                // than trying again on every launch.
                settings.setLastModelFile(null)
                return@launch
            }
            _ui.update { it.copy(startingUp = true, error = null) }
            runCatching { engine.load(file) }
                .onSuccess {
                    _ui.update { it.copy(startingUp = false, loadedModel = file.name) }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    // Land on the picker with the reason, rather than a blank
                    // screen that never finishes waking up.
                    settings.setLastModelFile(null)
                    _ui.update { it.copy(startingUp = false, error = describeFailure(e)) }
                }
        }
    }

    /** The model the app is coming back to, for the waking screen. */
    fun lastModelName(): String? = settings.settings.value.lastModelFile

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

    fun phoneControl(): Boolean = settings.settings.value.phoneControl

    fun setPhoneControl(on: Boolean) = settings.setPhoneControl(on)

    fun keepRescueCopy(): Boolean = settings.settings.value.keepRescueCopy

    fun setKeepRescueCopy(on: Boolean) = settings.setKeepRescueCopy(on)

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
                            saveRescueCopy(state.file)
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

    /**
     * Copies a finished download to Downloads in the background.
     *
     * Models live where Android deletes them on uninstall, which is how a two
     * gigabyte download has been lost twice. Best effort on purpose: a failed
     * copy must not turn a working download into an error, so it is reported
     * only as a note.
     */
    private fun saveRescueCopy(file: File) {
        if (!settings.settings.value.keepRescueCopy) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runtime.models.rescueCopy(file) }
                .onSuccess { path ->
                    _ui.update {
                        it.copy(
                            notice = getApplication<Application>()
                                .getString(R.string.notice_rescue_copy, path),
                        )
                    }
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
        if (!fromVoice) _ui.update { it.copy(voiceMode = false) }

        // A recognised command is carried out rather than described. This runs
        // before the model both because it is instant and because a small model
        // asked to emit a tool call gets it wrong often enough to matter.
        // Anything the router is unsure of falls through, which is the safe
        // direction: talking about a call is recoverable, placing one is not.
        val command = if (settings.settings.value.phoneControl) {
            IntentRouter.route(prompt)
        } else {
            null
        }
        if (command != null) {
            runCommand(prompt, command)
            return
        }

        if (_ui.value.loadedModel == null) return

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

    /** Carries out a recognised command and says what happened. */
    private fun runCommand(prompt: String, command: Command) {
        _ui.update {
            it.copy(
                messages = it.messages + ChatMessage(true, prompt),
                error = null,
            )
        }
        // A call or a message cannot be taken back, so neither happens straight
        // from a command. Both resolve to a real person first and are then
        // spelled out on screen for confirmation.
        when (command) {
            is Command.Call -> {
                resolveContact(command.who, message = null)
                return
            }

            is Command.SendSms -> {
                resolveContact(command.who, message = command.message)
                return
            }

            else -> Unit
        }
        viewModelScope.launch {
            val outcome = runtime.tools.run(command)
            val spoken = when (outcome) {
                is ToolOutcome.Done -> outcome.spoken
                is ToolOutcome.NotYet -> outcome.spoken
                is ToolOutcome.Failed -> outcome.spoken
            }
            _ui.update { it.copy(messages = it.messages + ChatMessage(false, spoken)) }
            if (_ui.value.speakReplies) speaker.speak(spoken)
            persistChat()
        }
    }

    /**
     * Turns a spoken name into a person, or asks. Never picks between two
     * plausible matches: the cost of being wrong is a call to the wrong number.
     */
    private fun resolveContact(who: String, message: String?) {
        val forCall = message == null
        // Every permission this needs is asked for up front. Checking only
        // contacts and discovering the missing one at the moment of dialling
        // surfaces as a SecurityException, which the user sees as "the call
        // could not be placed" — true, but useless.
        val required = listOf(
            Manifest.permission.READ_CONTACTS,
            if (forCall) Manifest.permission.CALL_PHONE else Manifest.permission.SEND_SMS,
        )
        val missing = required.filterNot(::granted)
        if (missing.isNotEmpty()) {
            askFor(
                missing,
                when {
                    Manifest.permission.READ_CONTACTS in missing ->
                        R.string.tool_no_contacts_permission

                    forCall -> R.string.tool_no_call_permission
                    else -> R.string.tool_no_sms_permission
                },
            )
            return
        }

        viewModelScope.launch {
            val book = withContext(Dispatchers.IO) { runtime.contacts.all() }
            when (val found = ContactMatcher.match(who, book)) {
                is ContactMatch.None -> say(
                    getApplication<Application>().getString(R.string.tool_no_contact, who),
                )

                is ContactMatch.Several ->
                    _ui.update { it.copy(ask = Ask.Choose(found.contacts, message)) }

                is ContactMatch.One -> confirm(found.contact, message)
            }
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun askFor(permissions: List<String>, reasonRes: Int) {
        _ui.update {
            it.copy(
                ask = Ask.NeedPermission(
                    permissions,
                    getApplication<Application>().getString(reasonRes),
                ),
            )
        }
    }

    private fun confirm(contact: Contact, message: String?) {
        val action = if (message == null) {
            Action.Call(contact)
        } else {
            Action.Sms(contact, message)
        }
        _ui.update { it.copy(ask = Ask.Confirm(action)) }
    }

    /** The user picked one of several matching people. */
    fun chooseContact(contact: Contact) {
        val message = (_ui.value.ask as? Ask.Choose)?.message
        confirm(contact, message)
    }

    /** The user said yes. This is the only path that places a call or sends a message. */
    fun confirmAsk() {
        val action = (_ui.value.ask as? Ask.Confirm)?.action ?: return
        _ui.update { it.copy(ask = null) }
        viewModelScope.launch {
            val outcome = runtime.tools.perform(action)
            say(
                when (outcome) {
                    is ToolOutcome.Done -> outcome.spoken
                    is ToolOutcome.NotYet -> outcome.spoken
                    is ToolOutcome.Failed -> outcome.spoken
                },
            )
        }
    }

    /**
     * Clears the request after Android has answered. A grant is not acted on by
     * itself: the user asks again, now that it can work.
     */
    fun permissionResult(granted: Boolean) {
        _ui.update { it.copy(ask = null) }
        say(
            getApplication<Application>().getString(
                if (granted) R.string.permission_granted else R.string.permission_denied,
            ),
        )
    }

    fun dismissAsk() {
        val hadAsked = _ui.value.ask != null
        _ui.update { it.copy(ask = null) }
        if (hadAsked) say(getApplication<Application>().getString(R.string.cancelled))
    }

    /** Adds a line from Jarvis and reads it out, without involving the model. */
    private fun say(text: String) {
        _ui.update { it.copy(messages = it.messages + ChatMessage(false, text)) }
        if (_ui.value.speakReplies) speaker.speak(text)
        persistChat()
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

    // ---- Updating in place ------------------------------------------------

    fun checkForUpdate() {
        if (_ui.value.update is UpdateUi.Checking) return
        _ui.update { it.copy(update = UpdateUi.Checking) }
        viewModelScope.launch {
            runtime.updates.latest()
                .onSuccess { found ->
                    _ui.update {
                        it.copy(
                            update = if (found == null) {
                                UpdateUi.UpToDate
                            } else {
                                UpdateUi.Available(found)
                            },
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _ui.update { it.copy(update = UpdateUi.Failed(describeFailure(e))) }
                }
        }
    }

    fun downloadUpdate() {
        val available = (_ui.value.update as? UpdateUi.Available)?.update ?: return
        _ui.update { it.copy(update = UpdateUi.Downloading(0, available.sizeBytes)) }
        viewModelScope.launch {
            runtime.updates.download(available) { done, total ->
                _ui.update { it.copy(update = UpdateUi.Downloading(done, total)) }
            }
                .onSuccess { file -> _ui.update { it.copy(update = UpdateUi.Ready(file)) } }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _ui.update { it.copy(update = UpdateUi.Failed(describeFailure(e))) }
                }
        }
    }

    fun installUpdate() {
        val ready = (_ui.value.update as? UpdateUi.Ready)?.file ?: return
        viewModelScope.launch {
            runtime.installer.install(ready).onFailure { e ->
                if (e is CancellationException) throw e
                _ui.update { it.copy(update = UpdateUi.Failed(describeFailure(e))) }
            }
        }
    }

    fun canInstallUpdates(): Boolean = runtime.installer.canInstall()

    fun allowInstallsIntent() = runtime.installer.allowInstallsIntent()

    fun dismissUpdate() = _ui.update { it.copy(update = UpdateUi.Idle) }

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
