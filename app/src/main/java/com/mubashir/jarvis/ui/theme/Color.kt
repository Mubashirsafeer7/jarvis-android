package com.mubashir.jarvis.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The one place any Jarvis colour is defined.
 *
 * The reactor used to keep its own private copy of the amber, so the palette and
 * the thing the palette exists for could drift apart without anyone noticing.
 */
object JarvisColors {

    // Amber: the reactor, and every accent that follows it.
    val Amber = Color(0xFFFF9D2E)
    val AmberBright = Color(0xFFFFC46B)
    val AmberDeep = Color(0xFFB85C00)
    val AmberInk = Color(0xFF1A0E00)
    val Core = Color(0xFFFFF1D6)

    // Navy ground, in the steps Material asks for. Without these the dialog —
    // the surface a user sees precisely when something has gone wrong — fell
    // back to Material's baseline purple-grey.
    val Navy = Color(0xFF060B14)
    val PanelLowest = Color(0xFF05090F)
    val PanelLow = Color(0xFF0A111B)
    val Panel = Color(0xFF0E1622)
    val PanelHigh = Color(0xFF141E2C)
    val PanelHighest = Color(0xFF1B2637)

    val Ink = Color(0xFFF3E7D6)
    val Muted = Color(0xFFB39A7C)

    /**
     * Borders carry meaning, so they have to clear 3:1 against the ground. The
     * old outline was about 1.7:1 — the text field's edge was effectively
     * invisible. This one measures near 4:1.
     */
    val Outline = Color(0xFF8A6E45)

    /** Decorative rules only; never the edge of a control. */
    val OutlineFaint = Color(0xFF3A2E1E)

    // Cool accent, taken from the circuitry in the reactor artwork. Used where
    // amber would read as a warning rather than as information.
    val Cyan = Color(0xFF6BC6FF)
    val CyanInk = Color(0xFF00243A)

    // Failure. Material's baseline red clashes badly with amber.
    val Ember = Color(0xFFFF6B5A)
    val EmberInk = Color(0xFF2A0600)
    val EmberContainer = Color(0xFF4E1409)
    val OnEmberContainer = Color(0xFFFFDAD4)
}
