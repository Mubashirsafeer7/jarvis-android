package com.mubashir.jarvis.llm

import kotlinx.coroutines.flow.Flow

/**
 * Whatever is doing the thinking.
 *
 * The phone can hold a 3B model. It can hold a conversation; it cannot plan a
 * job, write code that runs, or catch its own mistakes — and no amount of code
 * here changes that, because the limit is the size of the model rather than the
 * software around it. The interface that matters is therefore this one: the
 * screen, the voice, the tools and the permissions all stay exactly the same
 * whether the answer comes from this phone or from a machine at home running
 * something far larger.
 *
 * Making that swap possible now costs one interface. Making it possible later
 * costs rewriting everything that touches the engine.
 */
interface Brain {

    /** Shown in the header, so it is always obvious which brain answered. */
    val label: String

    /** False when there is nothing to ask — no model loaded, or no server reachable. */
    suspend fun isReady(): Boolean

    /** Streams the answer a token or a chunk at a time. */
    fun ask(prompt: String, predictLength: Int): Flow<String>

    /** Checks the brain can actually be reached, with a reason when it cannot. */
    suspend fun check(): Result<String>
}
