package com.mubashir.jarvis.sense

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Everything Jarvis can know about right now, gathered in one place.
 *
 * Read on the way to the model rather than kept up to date in the background.
 * A battery level that is polled every minute is a battery level that costs
 * battery, and none of this is worth knowing except at the moment somebody asks
 * something.
 */
class Senses(private val context: Context) {

    private val schedule = Schedule(context)

    suspend fun now(): Situation = withContext(Dispatchers.IO) {
        Situation(
            now = LocalDateTime.now(),
            batteryPercent = batteryPercent(),
            charging = charging(),
            nextAppointment = nextAppointment(),
            unreadCount = NoticeWords.worthSaying(
                NoticeBox.notices.value,
                System.currentTimeMillis(),
            ).size,
        )
    }

    private fun batteryStatus(): Intent? = runCatching {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }.getOrNull()

    private fun batteryPercent(): Int? {
        val status = batteryStatus() ?: return null
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return level * 100 / scale
    }

    private fun charging(): Boolean {
        val plugged = batteryStatus()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }

    /**
     * The next thing in the calendar, if the calendar can be read at all.
     *
     * Silently nothing without the permission. This runs on the way to every
     * message, and a permission prompt that appeared because somebody said
     * "hello" would be indefensible — the calendar command is where it is
     * asked for, at the moment it is actually wanted.
     */
    private suspend fun nextAppointment(): Appointment? {
        if (!schedule.canRead()) return null
        val now = LocalDateTime.now()
        return schedule.today()
            .filterNot { it.allDay }
            .filter { it.start.isAfter(now) }
            .minByOrNull { it.start }
    }
}
