package com.mubashir.jarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Every Material role is filled in, not just the eleven that happened to be
 * visible. The ones left unset fell back to Material's baseline purple — which
 * is why the error dialog, the single surface a user sees when something has
 * gone wrong, was the one place the app did not look like itself.
 */
private val Reactor = darkColorScheme(
    primary = JarvisColors.Amber,
    onPrimary = JarvisColors.AmberInk,
    primaryContainer = JarvisColors.AmberDeep,
    onPrimaryContainer = JarvisColors.Core,

    secondary = JarvisColors.AmberBright,
    onSecondary = JarvisColors.AmberInk,
    secondaryContainer = JarvisColors.PanelHighest,
    onSecondaryContainer = JarvisColors.Ink,

    tertiary = JarvisColors.Cyan,
    onTertiary = JarvisColors.CyanInk,
    tertiaryContainer = JarvisColors.CyanInk,
    onTertiaryContainer = JarvisColors.Cyan,

    background = JarvisColors.Navy,
    onBackground = JarvisColors.Ink,

    surface = JarvisColors.Panel,
    onSurface = JarvisColors.Ink,
    // Distinct from surface on purpose: equal to it, the progress track was
    // invisible against the card it sat on.
    surfaceVariant = JarvisColors.PanelHigh,
    onSurfaceVariant = JarvisColors.Muted,
    surfaceTint = JarvisColors.Amber,

    surfaceContainerLowest = JarvisColors.PanelLowest,
    surfaceContainerLow = JarvisColors.PanelLow,
    surfaceContainer = JarvisColors.Panel,
    surfaceContainerHigh = JarvisColors.PanelHigh,
    surfaceContainerHighest = JarvisColors.PanelHighest,
    surfaceDim = JarvisColors.Navy,
    surfaceBright = JarvisColors.PanelHighest,

    error = JarvisColors.Ember,
    onError = JarvisColors.EmberInk,
    errorContainer = JarvisColors.EmberContainer,
    onErrorContainer = JarvisColors.OnEmberContainer,

    outline = JarvisColors.Outline,
    outlineVariant = JarvisColors.OutlineFaint,

    inverseSurface = JarvisColors.Ink,
    inverseOnSurface = JarvisColors.Navy,
    inversePrimary = JarvisColors.AmberDeep,
    scrim = JarvisColors.Navy,
)

/**
 * One palette, always dark. A reactor glowing amber only reads against a dark
 * ground, so there is no light variant to fall back to — which is also why the
 * Activity has to tell the system its bars are dark rather than letting them
 * follow the phone's own light/dark setting.
 */
@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Reactor,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content,
    )
}
