package com.mubashir.jarvis

import com.arm.aichat.InferenceEngine
import com.mubashir.jarvis.llm.PrepStep
import com.mubashir.jarvis.llm.prepStepFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The engine only accepts a model load from Initialized. Getting the walk back to
 * that state wrong is what made model switching impossible and made one failed
 * load poison every later attempt, so the rules are pinned here.
 */
class EnginePrepTest {

    @Test
    fun `a freshly initialised engine can load straight away`() {
        assertEquals(PrepStep.Load, prepStepFor(InferenceEngine.State.Initialized))
    }

    @Test
    fun `switching models has to unload the one already loaded`() {
        // Without this the second load threw "Cannot load model in ModelReady!"
        // and the Model button could never actually change anything.
        assertEquals(PrepStep.CleanThenLoad, prepStepFor(InferenceEngine.State.ModelReady))
    }

    @Test
    fun `a failed load is recoverable rather than permanent`() {
        // The engine latches its last error and only cleanUp() clears it, so
        // retrying used to rethrow the same stale exception forever.
        val failed = InferenceEngine.State.Error(IOException("out of memory"))
        assertEquals(PrepStep.CleanThenLoad, prepStepFor(failed))
    }

    @Test
    fun `transient states are waited out, not acted on`() {
        val transient = listOf(
            InferenceEngine.State.Uninitialized,
            InferenceEngine.State.Initializing,
            InferenceEngine.State.LoadingModel,
            InferenceEngine.State.UnloadingModel,
            InferenceEngine.State.Benchmarking,
            InferenceEngine.State.ProcessingSystemPrompt,
            InferenceEngine.State.ProcessingUserPrompt,
            InferenceEngine.State.Generating,
        )
        transient.forEach { state ->
            assertEquals("$state should be waited out", PrepStep.Wait, prepStepFor(state))
        }
    }

    @Test
    fun `every resting state has a way forward`() {
        // A resting state with no move is a stuck engine, which is exactly the
        // failure this is here to prevent.
        val resting = listOf(
            InferenceEngine.State.Initialized,
            InferenceEngine.State.ModelReady,
            InferenceEngine.State.Error(IllegalStateException("boom")),
        )
        resting.forEach { state ->
            assertTrue("$state must not be a wait", prepStepFor(state) != PrepStep.Wait)
        }
    }
}
