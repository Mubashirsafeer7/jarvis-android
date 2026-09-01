package com.mubashir.jarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Amber = Color(0xFFFF9D2E)
private val AmberBright = Color(0xFFFFC46B)
private val Navy = Color(0xFF060B14)
private val Panel = Color(0xFF0E1622)
private val Ink = Color(0xFFF3E7D6)
private val Muted = Color(0xFFB39A7C)

private val Reactor = darkColorScheme(
    primary = Amber,
    onPrimary = Navy,
    secondary = AmberBright,
    onSecondary = Navy,
    background = Navy,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    outline = Color(0xFF3A2A18),
)

/**
 * One palette, always dark. A reactor glowing amber only reads against a dark
 * ground, so there is no light variant to fall back to.
 */
@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Reactor, content = content)
}
