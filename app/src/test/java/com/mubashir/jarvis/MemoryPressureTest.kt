package com.mubashir.jarvis

import android.content.ComponentCallbacks2
import com.mubashir.jarvis.core.givesUpModelAt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class MemoryPressureTest {

    @Test
    fun `switching to another app does not throw the model away`() {
        // The bug this whole file exists for. UI_HIDDEN is 20 and RUNNING_LOW
        // is 10, so "level >= RUNNING_LOW" silently included it — and it fires
        // every single time the user leaves the app.
        assertFalse(givesUpModelAt(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
    }

    @Test
    fun `ordinary pressure while running is not enough`() {
        assertFalse(givesUpModelAt(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
        assertFalse(givesUpModelAt(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
    }

    @Test
    fun `critical pressure while running gives it up`() {
        assertTrue(givesUpModelAt(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
    }

    @Test
    fun `being next in line to be killed gives it up`() {
        assertTrue(givesUpModelAt(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertTrue(givesUpModelAt(ComponentCallbacks2.TRIM_MEMORY_MODERATE))
        assertTrue(givesUpModelAt(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }

    @Test
    fun `the two scales are not compared against each other`() {
        // UI_HIDDEN sits numerically between RUNNING_CRITICAL and BACKGROUND
        // while meaning less than either. Any rule that is a single threshold
        // gets this wrong, which is why it is written as a match and not a >=.
        assertTrue(
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN >
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        )
        assertFalse(givesUpModelAt(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertTrue(givesUpModelAt(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
    }
}
