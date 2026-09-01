package com.mubashir.jarvis.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.mubashir.jarvis.ui.theme.JarvisColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** What the reactor is reacting to. */
enum class ReactorState { Idle, Listening, Thinking, Speaking }

// Taken from the palette rather than restated here. These four were previously
// a private copy of the theme's amber, so changing the theme quietly left the
// reactor — the thing the theme exists for — a different colour.
private val Amber get() = JarvisColors.Amber
private val AmberBright get() = JarvisColors.AmberBright
private val AmberDeep get() = JarvisColors.AmberDeep
private val Core get() = JarvisColors.Core

/**
 * The arc reactor.
 *
 * While listening this is driven by [level] — the real microphone loudness, not
 * a canned animation — so the rings open on the user's own voice and settle when
 * they stop. The other states each get a motion of their own so a glance at the
 * screen says what Jarvis is doing without reading a word.
 */
@Composable
fun ArcReactor(
    state: ReactorState,
    level: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "reactor")

    val slowSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing)),
        label = "slowSpin",
    )
    val fastSpin by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "fastSpin",
    )
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600), RepeatMode.Reverse),
        label = "breath",
    )
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing)),
        label = "wave",
    )

    // How far the rings sit open. Listening follows the voice; the rest breathe.
    val openTarget = when (state) {
        ReactorState.Idle -> 0.06f + breath * 0.05f
        ReactorState.Listening -> 0.10f + level * 0.55f
        ReactorState.Thinking -> 0.22f + breath * 0.10f
        ReactorState.Speaking -> 0.28f + breath * 0.18f
    }
    val open by animateFloatAsState(openTarget, tween(90), label = "open")

    val glowTarget = when (state) {
        ReactorState.Idle -> 0.45f + breath * 0.15f
        ReactorState.Listening -> 0.55f + level * 0.45f
        ReactorState.Thinking -> 0.70f + breath * 0.20f
        ReactorState.Speaking -> 0.85f + breath * 0.15f
    }
    val glow by animateFloatAsState(glowTarget, tween(120), label = "glow")

    val innerSpin = if (state == ReactorState.Thinking) fastSpin * 2f else fastSpin

    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)

        drawHalo(c, r, glow)
        rotate(slowSpin, c) { drawTickRing(c, r * (0.92f + open * 0.06f), glow) }
        rotate(-slowSpin * 0.6f, c) { drawSegmentRing(c, r * (0.74f + open * 0.10f), r * 0.05f, glow) }
        rotate(innerSpin, c) { drawArcTrio(c, r * (0.56f + open * 0.12f), r * 0.035f, glow) }
        drawCoreRings(c, r * (0.34f + open * 0.10f), glow)
        drawCore(c, r * (0.13f + open * 0.06f), glow)

        if (state == ReactorState.Speaking) drawOutwardWaves(c, r, wave, glow)
    }
}

private fun DrawScope.drawHalo(c: Offset, r: Float, glow: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Amber.copy(alpha = 0.22f * glow),
                Amber.copy(alpha = 0.06f * glow),
                Color.Transparent,
            ),
            center = c,
            radius = r,
        ),
        radius = r,
        center = c,
    )
}

/** The outer bezel: a broken ring of ticks, long every fifth. */
private fun DrawScope.drawTickRing(c: Offset, radius: Float, glow: Float) {
    drawCircle(
        color = AmberDeep.copy(alpha = 0.55f * glow),
        radius = radius,
        center = c,
        style = Stroke(width = radius * 0.012f),
    )
    val ticks = 60
    repeat(ticks) { i ->
        val angle = (i * 360f / ticks) * DEG
        val long = i % 5 == 0
        val inner = radius * if (long) 0.90f else 0.945f
        val alpha = (if (long) 0.95f else 0.45f) * glow
        drawLine(
            color = if (long) AmberBright.copy(alpha = alpha) else Amber.copy(alpha = alpha),
            start = Offset(c.x + cos(angle) * inner, c.y + sin(angle) * inner),
            end = Offset(c.x + cos(angle) * radius, c.y + sin(angle) * radius),
            strokeWidth = radius * if (long) 0.022f else 0.011f,
        )
    }
}

/** Chunky segments, the part that reads as machinery. */
private fun DrawScope.drawSegmentRing(c: Offset, radius: Float, width: Float, glow: Float) {
    val segments = 12
    val gap = 6f
    val sweep = 360f / segments - gap
    repeat(segments) { i ->
        drawArc(
            color = Amber.copy(alpha = (0.35f + 0.5f * ((i % 3) / 2f)) * glow),
            startAngle = i * (360f / segments),
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(c.x - radius, c.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = width),
        )
    }
}

/** Three long arcs that spin faster while thinking. */
private fun DrawScope.drawArcTrio(c: Offset, radius: Float, width: Float, glow: Float) {
    listOf(0f, 120f, 240f).forEachIndexed { i, start ->
        drawArc(
            color = AmberBright.copy(alpha = (0.7f - i * 0.12f) * glow),
            startAngle = start,
            sweepAngle = 74f,
            useCenter = false,
            topLeft = Offset(c.x - radius, c.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = width),
        )
    }
}

private fun DrawScope.drawCoreRings(c: Offset, radius: Float, glow: Float) {
    drawCircle(
        color = Amber.copy(alpha = 0.8f * glow),
        radius = radius,
        center = c,
        style = Stroke(width = radius * 0.10f),
    )
    drawCircle(
        color = AmberBright.copy(alpha = 0.5f * glow),
        radius = radius * 0.72f,
        center = c,
        style = Stroke(width = radius * 0.05f),
    )
}

private fun DrawScope.drawCore(c: Offset, radius: Float, glow: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Core, AmberBright, Amber.copy(alpha = 0f)),
            center = c,
            radius = radius * 2.6f,
        ),
        radius = radius * 2.6f,
        center = c,
        alpha = glow.coerceIn(0f, 1f),
    )
    drawCircle(color = Core.copy(alpha = glow), radius = radius, center = c)
}

/** Rings travelling outward while Jarvis speaks. */
private fun DrawScope.drawOutwardWaves(c: Offset, r: Float, wave: Float, glow: Float) {
    repeat(3) { i ->
        val phase = (wave + i / 3f) % 1f
        drawCircle(
            color = AmberBright.copy(alpha = (1f - phase) * 0.35f * glow),
            radius = r * (0.4f + phase * 0.6f),
            center = c,
            style = Stroke(width = r * 0.012f),
        )
    }
}

private const val DEG = (Math.PI / 180f).toFloat()
