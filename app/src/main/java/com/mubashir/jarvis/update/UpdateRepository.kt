package com.mubashir.jarvis.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Asks GitHub whether there is a newer build, and fetches it.
 *
 * Releases rather than Actions artifacts: an artifact needs an authenticated
 * API call and expires after ninety days, so an app could never fetch one. A
 * release asset has a plain unauthenticated URL, which is also the reason the
 * repository has to be public.
 *
 * HttpURLConnection rather than a client library — this is two requests, and a
 * dependency that cannot be resolved from where this project is built is a
 * dependency that cannot be checked before it ships.
 */
class UpdateRepository(private val context: Context) {

    fun installedVersionCode(): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }.getOrDefault(0L)

    suspend fun latest(): Result<AvailableUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(LATEST_URL)
            val update = UpdateCheck.parseLatest(body)
            update?.takeIf { UpdateCheck.isNewer(installedVersionCode(), it) }
        }
    }

    /**
     * Downloads into the app's own files, where an interrupted attempt cannot be
     * mistaken for a finished one: it is written beside the real name and moved
     * only once every byte the server promised has arrived.
     */
    suspend fun download(
        update: AvailableUpdate,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }

            val target = File(dir, "jarvis-${update.versionCode}.apk")
            val staging = File(dir, "${target.name}.part")

            val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("The server refused the download (HTTP $code).")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes

                connection.inputStream.use { input ->
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER)
                        var written = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                        output.flush()
                    }
                }

                if (total > 0 && staging.length() != total) {
                    error("Only part of the update arrived. Try again.")
                }
                target.delete()
                if (!staging.renameTo(target)) error("The update could not be saved.")
                target
            } catch (e: Throwable) {
                staging.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("GitHub answered HTTP $code. If the repository is private there is nothing to check.")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val LATEST_URL =
            "https://api.github.com/repos/Mubashirsafeer7/jarvis-android/releases/latest"
        const val DIR = "updates"
        const val TIMEOUT_MS = 20_000
        const val BUFFER = 64 * 1024
    }
}
