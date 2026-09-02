package com.mubashir.jarvis.sense

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Where the phone is, without Google Play Services.
 *
 * FusedLocationProvider is the usual answer and it is unavailable here twice
 * over: it lives on a Maven host this project cannot reach, and it needs Play
 * Services on the device. LocationManager is part of Android, works on any
 * phone including one with no Google apps at all, and is enough to answer
 * "where am I".
 *
 * The last known fix is used before asking for a new one. A fresh satellite fix
 * costs battery and takes up to a minute outdoors and forever indoors, and for
 * "where am I" a fix from four minutes ago is the same answer.
 */
class Place(private val context: Context) {

    fun canRead(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun lastKnown(): Fix? = withContext(Dispatchers.IO) {
        // Checked inline rather than through canRead(). The guard belongs next
        // to the call it guards — asking without the permission throws — and a
        // check one call away is one lint cannot follow either.
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext null
        }

        val manager = context.getSystemService(LocationManager::class.java)
            ?: return@withContext null

        // Every provider, newest fix wins. GPS is the most accurate and the
        // most often stale — indoors it can be hours old while the network
        // provider has something from a minute ago.
        val best = runCatching {
            manager.allProviders
                .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return@withContext null

        Fix(
            latitude = best.latitude,
            longitude = best.longitude,
            accuracy = best.accuracy,
            ageMinutes = ((System.currentTimeMillis() - best.time) / 60_000).coerceAtLeast(0),
        )
    }

    /**
     * Turns a position into a place name, when there is any way to.
     *
     * Needs the network. Offline — which is where this app expects to be — it
     * simply comes back null and the answer falls back to coordinates, which is
     * why PlaceWords is built to work without this.
     */
    @Suppress("DEPRECATION") // the callback form is API 33+; this still works
    suspend fun nameFor(fix: Fix): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(fix.latitude, fix.longitude, 1)
                ?.firstOrNull()
                ?.let { found ->
                    listOfNotNull(
                        found.subLocality ?: found.locality,
                        found.adminArea.takeIf { it != found.locality },
                    ).distinct().joinToString(", ").ifEmpty { found.getAddressLine(0) }
                }
        }.getOrNull()
    }

    /** True when the user has location switched off entirely, whatever the app is allowed. */
    fun switchedOff(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            !manager.isLocationEnabled
        } else {
            !manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }
}
