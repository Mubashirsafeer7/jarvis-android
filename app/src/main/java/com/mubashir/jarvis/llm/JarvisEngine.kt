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
        // 512 cut long answers off mid-sentence.
        const val DEFAULT_PREDICT = 1024
        const val INIT_TIMEOUT_MS = 30_000L

        // A 3B model writes far better English than Hinglish — asked for Hinglish
        // it produced sentences that were not really any language. So it answers
        // in English and still understands Hinglish questions.
        //
        // The boundary matters as much as the language: with no tools wired up
        // yet, it was cheerfully offering to "fix settings and connectivity",
        // which it cannot do at all. Saying plainly what it cannot do stops that.
        const val SYSTEM_PROMPT = """You are Jarvis, a personal assistant running entirely on Mubashir's phone.

How to answer:
- Answer in English, even when the question is in Hindi, Urdu or Hinglish. You understand all of them.
- Be brief: one or two sentences unless detail is asked for.
- Plain text only. No markdown, no emoji, no bullet points.
- If you do not know something, say so in one short sentence.

What you cannot do — say so plainly if asked, never pretend otherwise:
- You cannot make calls, send messages, or open apps.
- You cannot change any phone setting, or fix Wi-Fi, Bluetooth or connectivity.
- You cannot search the internet, check the weather, or read live information. You are offline.
- You cannot see the screen, the camera, files, contacts or notifications.

You can talk, explain, translate, summarise, do arithmetic, and help the user think."""
    }
}
