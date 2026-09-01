package com.mubashir.jarvis

import com.mubashir.jarvis.voice.MicLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicLevelTest {

    /** Feeds the same reading until the smoothing settles. */
    private fun MicLevel.settle(db: Float, steps: Int = 60): Float {
        repeat(steps) { update(db) }
        return value
    }

    @Test
    fun `silence stays at zero`() {
        assertEquals(0f, MicLevel().settle(-2f), 0.01f)
    }

    @Test
    fun `a loud voice reaches full`() {
        assertEquals(1f, MicLevel().settle(10f), 0.01f)
    }

    @Test
    fun `mid volume lands in the middle`() {
        assertEquals(0.5f, MicLevel().settle(4f), 0.02f)
    }

    @Test
    fun `readings outside the expected range are clamped`() {
        assertEquals(0f, MicLevel().settle(-120f), 0.001f)
        assertEquals(1f, MicLevel().settle(90f), 0.001f)
    }

    @Test
    fun `a NaN reading does not poison the level`() {
        val level = MicLevel()
        level.settle(10f)
        repeat(5) { level.update(Float.NaN) }
        assertTrue("value went bad: ${level.value}", level.value in 0f..1f)
        assertTrue(!level.value.isNaN())
    }

    @Test
    fun `one loud sample does not jump straight to full`() {
        // Smoothing is the point: a single spike must not snap the ring open.
        val level = MicLevel()
        val after = level.update(10f)
        assertTrue("jumped to $after on one sample", after < 0.7f)
        assertTrue("but should still move", after > 0f)
    }

    @Test
    fun `it rises faster than it falls`() {
        val rising = MicLevel().apply { update(10f) }.value

        val falling = MicLevel().apply {
            settle(10f)
            update(-2f)
        }.value
        val dropped = 1f - falling

        assertTrue(
            "attack $rising should outpace release $dropped",
            rising > dropped,
        )
    }

    @Test
    fun `reset returns to silence`() {
        val level = MicLevel()
        level.settle(10f)
        level.reset()
        assertEquals(0f, level.value, 0.001f)
    }
}
