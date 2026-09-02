package com.mubashir.jarvis.routine

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The standing instructions, on disk.
 *
 * Same shape as MemoryStore and for the same reasons: SQLite directly rather
 * than Room, because Room's compiler is a dependency this project cannot
 * resolve and the whole of this is one table.
 *
 * The trigger is stored as a kind and two numbers rather than as serialised
 * JSON. A routine that fails to deserialise is a routine that silently stops
 * happening, and the user has no way to tell the difference between that and
 * it simply not being time yet.
 */
class RoutineStore(context: Context) {

    private val helper = object : SQLiteOpenHelper(
        context.applicationContext, NAME, null, VERSION,
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    what TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    at_minutes INTEGER NOT NULL DEFAULT 0,
                    days TEXT NOT NULL DEFAULT '',
                    percent INTEGER NOT NULL DEFAULT 0,
                    before_minutes INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    last_run INTEGER
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { _routines.value = readAll() }

    suspend fun add(routine: Routine): Routine = withContext(Dispatchers.IO) {
        val id = helper.writableDatabase.insert(TABLE, null, routine.asRow())
        load()
        routine.copy(id = id)
    }

    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
        load()
    }

    suspend fun setEnabled(id: Long, on: Boolean) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            TABLE,
            ContentValues().apply { put("enabled", if (on) 1 else 0) },
            "id = ?",
            arrayOf(id.toString()),
        )
        load()
    }

    /**
     * Written the moment a routine fires, before it has finished doing
     * anything. If the work crashes or the process is killed halfway, the
     * routine has still been marked as run — which is the safer direction: a
     * missed briefing is a shrug, one that repeats every fifteen minutes until
     * the phone is restarted is not.
     */
    suspend fun markRun(id: Long, at: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.update(
            TABLE,
            ContentValues().apply { put("last_run", at) },
            "id = ?",
            arrayOf(id.toString()),
        )
        load()
    }

    /** Read straight from disk, for the background check that has no view of state. */
    fun allNow(): List<Routine> = readAll()

    private fun readAll(): List<Routine> {
        val rows = helper.readableDatabase.query(
            TABLE,
            arrayOf(
                "id", "what", "kind", "at_minutes", "days",
                "percent", "before_minutes", "enabled", "last_run",
            ),
            null, null, null, null, "id ASC",
        )
        val found = mutableListOf<Routine>()
        rows.use { cursor ->
            while (cursor.moveToNext()) {
                val trigger = when (cursor.getString(2)) {
                    KIND_DAILY -> Trigger.Daily(
                        at = LocalTime.ofSecondOfDay(cursor.getInt(3) * 60L),
                        days = cursor.getString(4)
                            .split(',')
                            .mapNotNull { name ->
                                runCatching { DayOfWeek.valueOf(name) }.getOrNull()
                            }
                            .toSet()
                            // An empty or unreadable day list would be a routine
                            // that can never fire, which looks identical to one
                            // that is simply broken.
                            .ifEmpty { Trigger.Daily.EVERY_DAY },
                    )

                    KIND_BATTERY -> Trigger.BatteryBelow(cursor.getInt(5))
                    KIND_BEFORE -> Trigger.BeforeAppointment(cursor.getInt(6))
                    else -> continue
                }
                found += Routine(
                    id = cursor.getLong(0),
                    what = cursor.getString(1),
                    trigger = trigger,
                    enabled = cursor.getInt(7) == 1,
                    lastRun = if (cursor.isNull(8)) null else cursor.getLong(8),
                )
            }
        }
        return found
    }

    private fun Routine.asRow() = ContentValues().apply {
        put("what", what)
        put("enabled", if (enabled) 1 else 0)
        lastRun?.let { put("last_run", it) }
        when (val t = trigger) {
            is Trigger.Daily -> {
                put("kind", KIND_DAILY)
                put("at_minutes", t.at.hour * 60 + t.at.minute)
                put("days", t.days.joinToString(",") { it.name })
            }

            is Trigger.BatteryBelow -> {
                put("kind", KIND_BATTERY)
                put("percent", t.percent)
            }

            is Trigger.BeforeAppointment -> {
                put("kind", KIND_BEFORE)
                put("before_minutes", t.minutes)
            }
        }
    }

    private companion object {
        const val NAME = "routines.db"
        const val VERSION = 1
        const val TABLE = "routines"
        const val KIND_DAILY = "daily"
        const val KIND_BATTERY = "battery"
        const val KIND_BEFORE = "before"
    }
}
