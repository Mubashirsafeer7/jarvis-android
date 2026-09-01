package com.mubashir.jarvis.core

import android.content.ComponentCallbacks2

/**
 * When Android asking for memory back is worth giving the model up for.
 *
 * Kept apart from [JarvisRuntime] because it is a rule over one integer, and
 * getting it wrong is invisible: the model quietly disappears and the app goes
 * on claiming to have it. That is exactly what happened — the old rule was
 * `level >= TRIM_MEMORY_RUNNING_LOW`, which reads like "only under pressure"
 * and is not. TRIM_MEMORY_UI_HIDDEN is a larger number than TRIM_MEMORY_RUNNING_LOW
 * and means nothing about memory at all: it fires every time the user switches
 * to another app. So the model was thrown away on the way out, every time, and
 * the next message waited thirty seconds for an engine holding nothing.
 *
 * The levels are not a severity scale. They are two scales — 5..15 for a
 * process that is running, 20..80 for one that is not — and comparing across
 * them is the whole bug.
 */
@Suppress("DEPRECATION") // the constants are deprecated; the callback still fires
internal fun givesUpModelAt(level: Int): Boolean = when (level) {
    // Not memory pressure. The user switched apps, which they do constantly.
    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> false

    // Running, and the system is critical. Two gigabytes back is worth more
    // than keeping it warm.
    ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> true

    // Backgrounded and on the kill list. Giving the model up beats being killed
    // outright, and it now comes back on its own when it is next needed.
    else -> level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
}
