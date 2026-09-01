package com.mubashir.jarvis.llm

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Jarvis's side of the llama.cpp engine: it owns the persona and the loading
 * rules, and leaves token generation to [InferenceEngine].
 */
class JarvisEngine(context: Context) {

    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)

    val state: StateFlow<InferenceEngine.State> = engine.state

    var loadedModelPath: String? = null
        private set

    suspend fun load(model: File) {
        if (loadedModelPath == model.absolutePath) return
        awaitInitialised()
        engine.loadModel(model.absolutePath)
        engine.setSystemPrompt(SYSTEM_PROMPT)
        loadedModelPath = model.absolutePath
    }

    /**
     * The engine loads its native library on a coroutine of its own, and
     * [InferenceEngine.loadModel] rejects anything that arrives before that
     * finishes. A model load issued the moment a download completes can easily
     * be that early, so wait for the engine to settle rather than racing it.
     */
    private suspend fun awaitInitialised() {
        val settled = withTimeoutOrNull(INIT_TIMEOUT_MS) {
            engine.state.first { state ->
                state !is InferenceEngine.State.Uninitialized &&
                    state !is InferenceEngine.State.Initializing
            }
        } ?: error("Engine tayyar nahi hua — native library load hone mein bahut waqt lag gaya")

        if (settled is InferenceEngine.State.Error) throw settled.exception
    }

    fun ask(prompt: String, predictLength: Int = DEFAULT_PREDICT): Flow<String> =
        engine.sendUserPrompt(prompt, predictLength)

    /**
     * Measures this phone rather than trusting a spec sheet: prompt-processing
     * and token-generation speed for the model that is loaded.
     */
    suspend fun benchmark(): String = engine.bench(pp = 128, tg = 32, pl = 1, nr = 1)

    fun unload() {
        engine.cleanUp()
        loadedModelPath = null
    }

    fun destroy() {
        engine.destroy()
        loadedModelPath = null
    }

    private companion object {
        const val DEFAULT_PREDICT = 512
        const val INIT_TIMEOUT_MS = 30_000L

        // Small models follow a short, concrete persona far better than a long
        // one, and drift into formal Hindi or pure English without being told
        // twice to stay in Hinglish.
        const val SYSTEM_PROMPT = """You are Jarvis, Mubashir's personal AI assistant.

Rules:
- Reply in Hinglish: Hindi words written in English letters, mixed with English. Never Devanagari.
- Be brief. One or two sentences unless asked for detail.
- You run fully offline on his phone. You have no internet, so never claim to look anything up.
- If you don't know, say so plainly.
- Speak like a capable, calm assistant — not a chatbot. No emoji, no filler."""
    }
}
