package com.mubashir.jarvis.llm

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Jarvis's side of the llama.cpp engine: it owns the persona and the loading
 * rules, and leaves token generation to [InferenceEngine].
 *
 * One instance per process, held by JarvisRuntime. That is not a preference —
 * AiChat.getInferenceEngine returns a process-wide singleton, so a second owner
 * with its own lifecycle would be fighting over the same native model.
 */
class JarvisEngine(context: Context) {

    private val engine: InferenceEngine = AiChat.getInferenceEngine(context.applicationContext)

    val state: StateFlow<InferenceEngine.State> = engine.state

    @Volatile
    var loadedModelPath: String? = null
        private set

    suspend fun load(model: File) {
        if (loadedModelPath == model.absolutePath) return
        prepareToLoad()
        engine.loadModel(model.absolutePath)
        engine.setSystemPrompt(SYSTEM_PROMPT)
        loadedModelPath = model.absolutePath
    }

    /**
     * Walks the engine back to the one state a load is accepted from, waiting out
     * anything transient and releasing anything held.
     *
     * The bounded loop matters: each round either settles the engine or gives up,
     * so a cleanUp() that does not take cannot spin here forever.
     */
    private suspend fun prepareToLoad() {
        repeat(MAX_PREP_ROUNDS) {
            val settled = withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                engine.state.first { prepStepFor(it) != PrepStep.Wait }
            } ?: error("The engine did not become ready in time.")

            when (prepStepFor(settled)) {
                PrepStep.Load -> return
                // cleanUp() blocks its calling thread on the engine's own
                // dispatcher, so it must never be called from the main thread.
                PrepStep.CleanThenLoad -> {
                    withContext(Dispatchers.IO) { engine.cleanUp() }
                    loadedModelPath = null
                }

                PrepStep.Wait -> Unit
            }
        }
        error("The engine did not become ready to load a model.")
    }

    /**
     * Waits until the engine will accept a prompt, then streams the answer.
     *
     * Stopping a reply returns the engine to ModelReady only once the cancellation
     * reaches its dispatcher, so sending again straight after Stop used to be
     * rejected outright and the message was dropped with an error dialog.
     */
    fun ask(prompt: String, predictLength: Int = DEFAULT_PREDICT): Flow<String> = flow {
        withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
            engine.state.first { it is InferenceEngine.State.ModelReady }
        } ?: error("Jarvis is still busy with the last answer.")

        emitAll(engine.sendUserPrompt(prompt, predictLength))
    }

    /**
     * Measures this phone rather than trusting a spec sheet: prompt-processing
     * and token-generation speed for the model that is loaded.
     */
    suspend fun benchmark(): String = engine.bench(pp = 128, tg = 32, pl = 1, nr = 1)

    /** Releases the model and the memory it holds, leaving the engine reusable. */
    suspend fun unload() {
        if (prepStepFor(engine.state.value) != PrepStep.CleanThenLoad) return
        withContext(Dispatchers.IO) { engine.cleanUp() }
        loadedModelPath = null
    }

    // There is deliberately no destroy(). InferenceEngine.destroy() cancels the
    // scope that loaded the native library and never clears the process-wide
    // instance it belongs to, so a later launch in the same process finds a dead
    // engine still claiming a model is ready — every load then fails until the
    // app is force-stopped, and a second destroy double-frees natively. Holding
    // the engine for the life of the process avoids the whole class of problem;
    // unload() is what callers actually want.

    private companion object {
        // 512 cut long answers off mid-sentence.
        const val DEFAULT_PREDICT = 1024
        const val SETTLE_TIMEOUT_MS = 30_000L
        const val MAX_PREP_ROUNDS = 4

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
