package com.mubashir.jarvis

import com.arm.aichat.UnsupportedArchitectureException

/**
 * Turns a failure into something worth reading.
 *
 * The inference engine throws [UnsupportedArchitectureException] with no message
 * at all, so the obvious `e.message ?: "Model load nahi hua"` showed nothing but
 * its fallback and hid a native packaging problem behind a generic dialog.
 *
 * The rule here: never show a bare fallback. If an exception carries no message,
 * name the exception, so the next failure points at itself.
 */
fun describeFailure(e: Throwable): String = when (e) {
    is UnsupportedArchitectureException ->
        "Model load nahi hua: llama.cpp ko is phone ke liye koi CPU backend nahi mila. " +
            "Yeh model file ka masla nahi — app ke native setup ka hai."

    else -> e.message?.takeIf(String::isNotBlank)
        ?: "${e.javaClass.simpleName} — koi detail nahi mili"
}
