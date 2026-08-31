package com.mubashir.jarvis

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCapabilitiesTest {

    private fun withRamGb(gb: Double) = DeviceCapabilities(
        totalRamBytes = (gb * 1_073_741_824.0).toLong(),
        availableRamBytes = 0,
        cpuCores = 8,
        abis = listOf("arm64-v8a"),
        androidSdk = 36,
        deviceName = "test",
    )

    @Test
    fun `12GB phone is high tier`() {
        // The realme 15 this is built for reports ~11.x GB of its 12 GB.
        assertEquals(DeviceCapabilities.Tier.HIGH, withRamGb(11.3).tier)
    }

    @Test
    fun `8GB phone is high tier`() {
        assertEquals(DeviceCapabilities.Tier.HIGH, withRamGb(7.6).tier)
    }

    @Test
    fun `6GB phone is medium tier`() {
        assertEquals(DeviceCapabilities.Tier.MEDIUM, withRamGb(5.7).tier)
    }

    @Test
    fun `4GB phone is low tier`() {
        assertEquals(DeviceCapabilities.Tier.LOW, withRamGb(3.6).tier)
    }

    @Test
    fun `bytes convert to gigabytes`() {
        assertEquals(8.0, withRamGb(8.0).totalRamGb, 0.01)
    }
}
