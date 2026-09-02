package com.mubashir.jarvis.core

import android.content.ComponentCallbacks2
import android.content.Context
import com.mubashir.jarvis.DeviceCapabilities
import com.mubashir.jarvis.data.ChatStore
import com.mubashir.jarvis.data.SettingsStore
import com.mubashir.jarvis.data.BrainChoice
import com.mubashir.jarvis.llm.Brain
import com.mubashir.jarvis.llm.JarvisEngine
import com.mubashir.jarvis.llm.LocalBrain
import com.mubashir.jarvis.llm.RemoteBrain
import com.mubashir.jarvis.memory.MemoryStore
import com.mubashir.jarvis.model.DownloadStore
import com.mubashir.jarvis.model.ModelManager
import com.mubashir.jarvis.tools.Contacts
import com.mubashir.jarvis.tools.ToolRunner
import com.mubashir.jarvis.update.ApkInstaller
import com.mubashir.jarvis.update.UpdateNotifier
import com.mubashir.jarvis.update.UpdateRepository
import com.mubashir.jarvis.voice.Speaker
import com.mubashir.jarvis.voice.VoiceInput
import com.mubashir.jarvis.voice.WakeModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Everything with a lifetime longer than a screen.
 *
 * The engine holds a multi-gigabyte model in native memory and the recogniser and
 * speaker each hold a system service binding. Owned by a ViewModel, all three were
 * torn down and rebuilt whenever the Activity went away — which cost a full model
 * reload at best, and at worst left the process-wide native engine dead. They
 * belong to the process, so they live here and the ViewModel only borrows them.
 */
class JarvisRuntime(context: Context) : ComponentCallbacks2 {

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob())

    val engine = JarvisEngine(app)
    val speaker = Speaker(app)
    val voice = VoiceInput(app)
    val wakeModel = WakeModelStore(app)
    val models = ModelManager(app)
    val downloads = DownloadStore(app)
    val settings = SettingsStore(app)
    val chats = ChatStore(app)
    val memory = MemoryStore(app)
    val tools = ToolRunner(app)
    val contacts = Contacts(app)
    val updates = UpdateRepository(app)
    val installer = ApkInstaller(app)
    val notifier = UpdateNotifier(app)
    val capabilities = DeviceCapabilities.read(app)

    init {
        // Read once, up front. Every prompt consults it, and a disk read on the
        // way to the model would put SQLite in the path of every message.
        scope.launch { memory.load() }
    }

    private val localBrain = LocalBrain(engine)
    private val remoteBrain = RemoteBrain(
        baseUrl = { settings.settings.value.serverUrl },
        model = { settings.settings.value.serverModel },
        systemPrompt = JarvisEngine.systemPrompt(),
    )

    /** Whichever brain is selected right now. */
    val brain: Brain
        get() = when (settings.settings.value.brain) {
            BrainChoice.Phone -> localBrain
            BrainChoice.Server -> remoteBrain
        }

    /**
     * Android asks for memory back before it starts killing processes. A loaded
     * model is by far the largest thing this app holds, so give it up rather than
     * be killed outright — the file is still on disk and reloads on demand.
     *
     * Which levels count is decided by [givesUpModelAt], and is not the obvious
     * threshold: see the note there.
     */
    @Suppress("DEPRECATION") // the levels are deprecated; the callback still fires
    override fun onTrimMemory(level: Int) {
        if (!givesUpModelAt(level)) return
        scope.launch {
            speaker.stop()
            engine.unload()
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Required by ComponentCallbacks2", ReplaceWith(""))
    override fun onLowMemory() = onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit
}
