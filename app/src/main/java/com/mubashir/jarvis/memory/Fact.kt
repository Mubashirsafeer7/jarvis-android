package com.mubashir.jarvis.memory

/** How a fact came to be known. */
enum class FactSource {
    /** The user said "remember this". Never removed on Jarvis's own initiative. */
    Told,

    /** Jarvis worked it out from a conversation. Shown separately, and removable. */
    Noticed,
}

/**
 * One thing Jarvis knows about its owner.
 *
 * Deliberately a short sentence rather than a key and a value. A phone-sized
 * model does far better with "Mubashir's brother is called Ali" than with
 * `brother = Ali`, and the things worth remembering do not fit a schema anyway
 * — "prefers short answers" is as much a fact as a name.
 *
 * @param topic the few words this fact is *about*, for finding it again and for
 *   noticing that a newer fact replaces it
 */
data class Fact(
    val id: Long = 0,
    val text: String,
    val topic: String,
    val source: FactSource,
    val createdAt: Long,
    /** Kept at the top and never crowded out of a prompt. */
    val pinned: Boolean = false,
)
