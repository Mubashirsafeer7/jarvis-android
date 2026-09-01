package com.mubashir.jarvis.llm

import kotlinx.coroutines.flow.Flow
import java.io.File

/** The model on this phone. */
class LocalBrain(private val engine: JarvisEngine) : Brain {

    override val label: String
        get() = engine.loadedModelPath?.let { File(it).nameWithoutExtension } ?: "No model"

    override suspend fun isReady(): Boolean = engine.loadedModelPath != null

    override fun ask(prompt: String, predictLength: Int): Flow<String> =
        engine.ask(prompt, predictLength)

    override suspend fun check(): Result<String> =
        if (engine.loadedModelPath != null) {
            Result.success(label)
        } else {
            Result.failure(IllegalStateException("No model is loaded on this phone."))
        }
}
