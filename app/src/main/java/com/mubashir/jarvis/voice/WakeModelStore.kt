package com.mubashir.jarvis.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** Where the acoustic model has got to. */
sealed interface WakeModelState {
    data object Missing : WakeModelState
    data class Fetching(val downloaded: Long, val total: Long) : WakeModelState
    data object Unpacking : WakeModelState
    data class Ready(val path: String) : WakeModelState
    data class Failed(val reason: String) : WakeModelState
}

/**
 * Fetches and unpacks the acoustic model, once.
 *
 * Deliberately not through DownloadManager, which the language models use.
 * DownloadManager cannot write into app-private storage, and this file has to
 * end up somewhere no other app can replace it — an acoustic model is loaded
 * straight into native code, so a file anyone can overwrite is a file anyone can
 * choose the contents of. Forty megabytes over one connection needs none of what
 * DownloadManager exists to provide.
 */
class WakeModelStore(context: Context) {

    private val app = context.applicationContext
    private val root = File(app.filesDir, "vosk")

    private val _state = MutableStateFlow<WakeModelState>(WakeModelState.Missing)
    val state: StateFlow<WakeModelState> = _state.asStateFlow()

    init {
        installedPath()?.let { _state.value = WakeModelState.Ready(it) }
    }

    /** The unpacked model, or null when there is not a complete one. */
    fun installedPath(): String? {
        val dirs: List<File> = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        val children: Map<String, List<String>> = dirs.associate { dir ->
            dir.name to (dir.list()?.toList() ?: emptyList())
        }
        val name = WakeModel.modelRoot(children) ?: return null
        return File(root, name).absolutePath
    }

    fun isInstalled(): Boolean = installedPath() != null

    /**
     * Downloads and unpacks the model. Safe to call when it is already there —
     * it returns the existing one rather than fetching it again.
     */
    suspend fun install(): WakeModelState = withContext(Dispatchers.IO) {
        installedPath()?.let {
            val ready = WakeModelState.Ready(it)
            _state.value = ready
            return@withContext ready
        }

        val result = runCatching {
            root.mkdirs()
            val zip = File(root, "model.zip.part")
            download(zip)

            _state.value = WakeModelState.Unpacking
            unzip(zip, root)
            zip.delete()

            installedPath() ?: throw IOException("The downloaded model is missing its parts.")
        }

        val state = result.fold(
            onSuccess = { WakeModelState.Ready(it) },
            onFailure = { error ->
                // A half-written directory is worse than none: it would be found
                // by installedPath() on the next launch and loaded as if it were
                // whole. Anything left behind goes.
                root.deleteRecursively()
                WakeModelState.Failed(reasonFor(error))
            },
        )
        _state.value = state
        state
    }

    /** Frees the disk the model is using, and stops it being found again. */
    fun remove() {
        root.deleteRecursively()
        _state.value = WakeModelState.Missing
    }

    private suspend fun download(into: File) {
        val connection = (URL(WakeModel.URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("The download server answered $code.")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: WakeModel.APPROX_BYTES
            _state.value = WakeModelState.Fetching(0, total)

            connection.inputStream.use { input ->
                FileOutputStream(into).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        // Once per megabyte. Every buffer would be a thousand
                        // recompositions for a bar that moves in millimetres.
                        if (done - lastReported >= 1024 * 1024) {
                            lastReported = done
                            _state.value = WakeModelState.Fetching(done, total)
                        }
                    }
                    _state.value = WakeModelState.Fetching(done, total)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun unzip(zip: File, into: File) {
        ZipInputStream(zip.inputStream().buffered()).use { stream ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = stream.nextEntry ?: break
                val name = WakeModel.safeEntryName(entry.name)
                if (name == null) {
                    stream.closeEntry()
                    continue
                }
                val target = File(into, name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output -> stream.copyTo(output) }
                }
                stream.closeEntry()
            }
        }
    }

    private fun reasonFor(error: Throwable): String = when (error) {
        is java.net.UnknownHostException,
        is java.net.SocketTimeoutException,
        is java.net.ConnectException,
        -> "Could not reach the download server. Check the connection and try again."

        is IOException -> error.message ?: "The download did not finish."
        else -> error.message ?: "The wake word model could not be installed."
    }
}
