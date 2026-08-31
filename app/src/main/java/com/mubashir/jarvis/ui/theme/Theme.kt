package com.mubashir.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Cyan = Color(0xFF4DD0E1)
private val CyanBright = Color(0xFFB2EBF2)
private val DeepNavy = Color(0xFF0B1622)
private val Panel = Color(0xFF13212F)

private val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = DeepNavy,
    secondary = CyanBright,
    background = DeepNavy,
    onBackground = Color(0xFFE3F2F5),
    surface = Panel,
    onSurface = Color(0xFFE3F2F5),
    surfaceVariant = Panel,
    onSurfaceVariant = Color(0xFF9FB6C4),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00778A),
    secondary = Color(0xFF00606F),
)

@Composable
fun JarvisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
