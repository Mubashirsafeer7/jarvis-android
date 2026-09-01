package com.mubashir.jarvis.llm

import com.arm.aichat.InferenceEngine

/**
 * What has to happen before [InferenceEngine.loadModel] will accept a call.
 *
 * The engine only accepts a load from [InferenceEngine.State.Initialized]. Every
 * other resting state has to be walked back to it first, and getting that wrong
 * is what made the app refuse to load a model for the rest of its life:
 *
 *  - [InferenceEngine.State.ModelReady] — a model is already loaded, so loading a
 *    second one throws. Unloading first is the only way to switch models.
 *  - [InferenceEngine.State.Error] — the engine latches its last failure and only
 *    `cleanUp()` clears it, so without this a single bad load poisoned every
 *    retry with the same stale exception.
 *
 * Kept apart from [JarvisEngine] because it is pure logic over a sealed class and
 * can therefore be tested on the JVM, unlike anything that touches the engine.
 */
enum class PrepStep {
    /** A transient state — wait for the engine to settle and ask again. */
    Wait,

    /** Ready to load. */
    Load,

    /** Resting, but holding something that has to be released first. */
    CleanThenLoad,
}

internal fun prepStepFor(state: InferenceEngine.State): PrepStep = when (state) {
    is InferenceEngine.State.Initialized -> PrepStep.Load

    is InferenceEngine.State.ModelReady,
    is InferenceEngine.State.Error,
    -> PrepStep.CleanThenLoad

    is InferenceEngine.State.Uninitialized,
    is InferenceEngine.State.Initializing,
    is InferenceEngine.State.LoadingModel,
    is InferenceEngine.State.UnloadingModel,
    is InferenceEngine.State.Benchmarking,
    is InferenceEngine.State.ProcessingSystemPrompt,
    is InferenceEngine.State.ProcessingUserPrompt,
    is InferenceEngine.State.Generating,
    -> PrepStep.Wait
}
