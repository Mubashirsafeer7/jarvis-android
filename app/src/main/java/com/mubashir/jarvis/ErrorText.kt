package com.mubashir.jarvis

import com.arm.aichat.UnsupportedArchitectureException

/**
 * Turns a failure into something worth reading.
 *
 * The inference engine throws [UnsupportedArchitectureException] with no message
 * at all, so the obvious `e.message ?: "could not load"` showed nothing but its
 * fallback and hid a native packaging problem behind a generic dialog.
 *
 * The rule here: never show a bare fallback. If an exception carries no message,
 * name the exception, so the next failure points at itself.
 */
fun describeFailure(e: Throwable): String = when (e) {
    // The native layer returns the same code for every load failure, so this
    // covers running out of memory and a damaged file as well as a missing
    // backend. Naming only one of them told the user the wrong thing on what is
    // in practice the most likely one.
    is UnsupportedArchitectureException ->
        "The model would not load. Usually that means the phone ran out of " +
            "memory for a model this size, or the file is damaged — try a " +
            "smaller model, or download this one again."

    is OutOfMemoryError ->
        "The phone ran out of memory loading that model. Try a smaller one."

    else -> e.message?.takeIf(String::isNotBlank)
        ?: "${e.javaClass.simpleName} — no further detail"
}
