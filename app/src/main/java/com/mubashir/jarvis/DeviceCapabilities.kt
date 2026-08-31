package com.mubashir.jarvis

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * What this phone can actually run. The model a build should default to depends
 * on physical RAM, so read it once here rather than hardcoding a device.
 */
data class DeviceCapabilities(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val cpuCores: Int,
    val abis: List<String>,
    val androidSdk: Int,
    val deviceName: String,
) {
    val totalRamGb: Double get() = totalRamBytes / 1_073_741_824.0
    val availableRamGb: Double get() = availableRamBytes / 1_073_741_824.0

    /** Physical RAM only: virtual/swap RAM does not help a memory-mapped model. */
    val tier: Tier get() = when {
        totalRamGb >= 7.5 -> Tier.HIGH
        totalRamGb >= 5.5 -> Tier.MEDIUM
        else -> Tier.LOW
    }

    enum class Tier { LOW, MEDIUM, HIGH }

    companion object {
        fun read(context: Context): DeviceCapabilities {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
            return DeviceCapabilities(
                totalRamBytes = info.totalMem,
                availableRamBytes = info.availMem,
                cpuCores = Runtime.getRuntime().availableProcessors(),
                abis = Build.SUPPORTED_ABIS.toList(),
                androidSdk = Build.VERSION.SDK_INT,
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        }
    }
}
