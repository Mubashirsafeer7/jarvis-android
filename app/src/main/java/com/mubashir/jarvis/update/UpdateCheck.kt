package com.mubashir.jarvis.update

import org.json.JSONObject

/**
 * The newest release, as GitHub describes it.
 *
 * @param versionCode taken from the tag, so it can be compared against the
 * running build without a second source of truth.
 */
data class AvailableUpdate(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val notes: String,
)

/**
 * Reading a GitHub release, kept apart from fetching one so the parsing can be
 * tested without a network — which matters here because the failure this guards
 * against is an update that installs the wrong thing, and that is not something
 * to discover on the phone.
 */
object UpdateCheck {

    /**
     * Releases are tagged `v<versionName>` where the last segment is also the
     * versionCode CI built with, so the tag alone orders two builds. Anything
     * that does not parse is treated as no update rather than as version zero:
     * a hand-made tag must never look newer or older by accident.
     */
    fun versionCodeOf(tag: String): Long? {
        val trimmed = tag.removePrefix("v").trim()
        if (trimmed.isEmpty()) return null
        val last = trimmed.substringAfterLast('.')
        return last.toLongOrNull()?.takeIf { it > 0 }
    }

    fun parseLatest(json: String): AvailableUpdate? {
        val release = try {
            JSONObject(json)
        } catch (e: Exception) {
            // A 404 page, an empty body, anything at all — none of it is an update.
            return null
        }
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null

        val tag = release.optString("tag_name")
        val code = versionCodeOf(tag) ?: return null

        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (url.isBlank()) continue
            return AvailableUpdate(
                versionCode = code,
                versionName = tag.removePrefix("v"),
                apkUrl = url,
                sizeBytes = asset.optLong("size"),
                notes = release.optString("body").trim(),
            )
        }
        // A release can exist with the build that should have attached the APK
        // having failed after it.
        return null
    }

    /** Strictly newer. Re-offering the build already installed is worse than silence. */
    fun isNewer(installed: Long, available: AvailableUpdate): Boolean =
        available.versionCode > installed
}
