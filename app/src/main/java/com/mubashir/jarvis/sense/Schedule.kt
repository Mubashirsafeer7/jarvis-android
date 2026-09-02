package com.mubashir.jarvis.sense

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Today, out of the phone's own calendar.
 *
 * CalendarContract rather than any Google library: the calendar provider is
 * part of Android, works with whatever accounts are already set up, and needs
 * no dependency this project cannot resolve. It also keeps the promise the app
 * makes everywhere else — nothing about this leaves the phone.
 *
 * Instances rather than Events, which matters more than it sounds: Events holds
 * "every Tuesday at ten" once, and asking it what is on today returns nothing
 * for a recurring meeting. Instances is the expanded view, and is the only one
 * that answers the question people actually ask.
 */
class Schedule(private val context: Context) {

    fun canRead(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun today(zone: ZoneId = ZoneId.systemDefault()): List<Appointment> =
        withContext(Dispatchers.IO) {
            if (!canRead()) return@withContext emptyList()

            val day = LocalDate.now(zone)
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val until = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(from.toString())
                .appendPath(until.toString())
                .build()

            val columns = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION,
            )

            val found = mutableListOf<Appointment>()
            runCatching {
                context.contentResolver.query(
                    uri, columns, null, null, "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { rows ->
                    while (rows.moveToNext()) {
                        val allDay = rows.getInt(3) == 1
                        found += Appointment(
                            title = rows.getString(0).orEmpty(),
                            start = rows.getLong(1).asLocal(zone, allDay),
                            end = rows.getLong(2).takeIf { it > 0 }?.asLocal(zone, allDay),
                            allDay = allDay,
                            where = rows.getString(4),
                        )
                    }
                }
            }
            found
        }

    /**
     * An all-day event is stored at midnight UTC rather than midnight here, so
     * reading it in the local zone puts a holiday on the wrong day for anyone
     * far enough east or west. Those are read as UTC and the rest locally.
     */
    private fun Long.asLocal(zone: ZoneId, allDay: Boolean): LocalDateTime =
        LocalDateTime.ofInstant(
            Instant.ofEpochMilli(this),
            if (allDay) ZoneId.of("UTC") else zone,
        )
}
