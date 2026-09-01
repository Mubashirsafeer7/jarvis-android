package com.mubashir.jarvis.core

import android.content.ComponentCallbacks2
import android.content.Context
import com.mubashir.jarvis.DeviceCapabilities
import com.mubashir.jarvis.data.ChatStore
import com.mubashir.jarvis.data.SettingsStore
import com.mubashir.jarvis.llm.JarvisEngine
import com.mubashir.jarvis.model.DownloadStore
import com.mubashir.jarvis.model.ModelManager
import com.mubashir.jarvis.voice.Speaker
import com.mubashir.jarvis.voice.VoiceInput
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
    val models = ModelManager(app)
    val downloads = DownloadStore(app)
    val settings = SettingsStore(app)
    val chats = ChatStore(app)
    val capabilities = DeviceCapabilities.read(app)

    /**
     * Android asks for memory back before it starts killing processes. A loaded
     * model is by far the largest thing this app holds, so give it up rather than
     * be killed outright — the file is still on disk and reloads on demand.
     */
    @Suppress("DEPRECATION") // the levels are deprecated; the callback still fires
    override fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
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
