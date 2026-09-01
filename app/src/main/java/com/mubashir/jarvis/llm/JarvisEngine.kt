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
        // Checked before waiting, because these are different problems with
        // different answers and only one of them is worth waiting out. Waiting
        // thirty seconds and then blaming the last answer — when the engine was
        // holding no model at all — described the wrong thing entirely and told
        // the user nothing they could act on.
        if (loadedModelPath == null) error("No model is loaded.")

        withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
            engine.state.first { it is InferenceEngine.State.ModelReady }
        } ?: error(settleFailureFor(engine.state.value))

        emitAll(engine.sendUserPrompt(prompt, predictLength))
    }

    /** Says which of the several ways this waits forever actually happened. */
    private fun settleFailureFor(state: InferenceEngine.State): String = when (state) {
        is InferenceEngine.State.Error ->
            "The engine stopped with an error. Load the model again from Settings."

        is InferenceEngine.State.Generating,
        is InferenceEngine.State.ProcessingUserPrompt,
        -> "Jarvis is still busy with the last answer."

        is InferenceEngine.State.LoadingModel,
        is InferenceEngine.State.ProcessingSystemPrompt,
        -> "Jarvis is still getting the model ready. Try again in a moment."

        else -> "The model is not ready. Load it again from Settings."
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

    companion object {
        /** The persona, shared with any brain that answers on Jarvis's behalf. */
        fun systemPrompt(): String = SYSTEM_PROMPT

        // 512 cut long answers off mid-sentence.
        private const val DEFAULT_PREDICT = 1024
        private const val SETTLE_TIMEOUT_MS = 30_000L
        private const val MAX_PREP_ROUNDS = 4

        // A 3B model writes far better English than Hinglish — asked for Hinglish
        // it produced sentences that were not really any language. So it answers
        // in English and still understands Hinglish questions.
        //
        // The boundary matters as much as the language. This list has to track
        // what the tools actually do: it was written when nothing was wired up,
        // and leaving it that way would have the model deny things it can now
        // do. Commands themselves never reach the model — the router carries
        // those out directly — but questions about them do, and the answer to
        // "can you turn on the torch?" has to be true.
        private const val SYSTEM_PROMPT = """You are Jarvis, a personal assistant running entirely on Mubashir's phone.

How to answer:
- Answer in English, even when the question is in Hindi, Urdu or Hinglish. You understand all of them.
- Be brief: one or two sentences unless detail is asked for.
- Plain text only. No markdown, no emoji, no bullet points.
- If you do not know something, say so in one short sentence.

What you can do on the phone, when asked directly:
- Turn the torch on and off.
- Say what the battery level is.
- Set a timer.
- Open an app by name.
- Call someone in the contacts, or send them a message. Both are shown on screen
  and confirmed by the user before anything happens.

What you cannot do — say so plainly if asked, never pretend otherwise:
- You cannot check location, read the calendar, or read notifications yet.
- You cannot change other phone settings, or fix Wi-Fi, Bluetooth or connectivity.
- You cannot search the internet, check the weather, or read live information. You are offline.
- You cannot see the screen, the camera, files or contacts.

You can talk, explain, translate, summarise, do arithmetic, and help the user think."""
    }
}
