package com.mubashir.jarvis.sense

import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the phone thinks it is. */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    /** Metres. Large numbers mean a tower fix rather than satellites. */
    val accuracy: Float,
    /** How old the fix is, in minutes. */
    val ageMinutes: Long,
)

/**
 * A position, said in a way that is honest about how sure it is.
 *
 * The temptation is to read out coordinates, which nobody can use, or to state
 * a street as fact when the fix is a cell tower two kilometres wide. Saying
 * "somewhere near" costs one word and is the difference between an assistant
 * that is useful and one that is confidently wrong.
 */
object PlaceWords {

    /** Beyond this the fix is a neighbourhood, not a place. */
    const val VAGUE_ABOVE_METRES = 500

    /** Beyond this it is not where you are now, it is where you were. */
    const val STALE_AFTER_MINUTES = 15L

    fun describe(fix: Fix?, address: String?): String {
        if (fix == null) {
            return "I cannot get a position. Location may be switched off, or the phone has not " +
                "had a fix since it started."
        }

        val place = address?.trim()?.takeIf { it.isNotEmpty() }
        val where = when {
            place != null && fix.accuracy <= VAGUE_ABOVE_METRES -> place
            place != null -> "somewhere near $place"
            else -> coordinates(fix)
        }

        val age = when {
            fix.ageMinutes >= 60 -> ", from ${fix.ageMinutes / 60} hours ago"
            fix.ageMinutes >= STALE_AFTER_MINUTES -> ", from ${fix.ageMinutes} minutes ago"
            else -> ""
        }

        val howSure = if (place == null && fix.accuracy > VAGUE_ABOVE_METRES) {
            ", give or take ${fix.accuracy.roundToInt()} metres"
        } else {
            ""
        }

        return "You are $where$howSure$age."
    }

    /**
     * Last resort, when there is no address — offline, or nowhere the geocoder
     * knows. Four decimals is about eleven metres, which is as much precision
     * as any of this deserves.
     */
    private fun coordinates(fix: Fix): String {
        val ns = if (fix.latitude >= 0) "north" else "south"
        val ew = if (fix.longitude >= 0) "east" else "west"
        return "at %.4f $ns, %.4f $ew".format(abs(fix.latitude), abs(fix.longitude))
    }
}
