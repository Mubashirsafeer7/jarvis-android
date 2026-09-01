package com.mubashir.jarvis.model

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

data class InstalledModel(
    val file: File,
    val spec: ModelSpec?,
    val sizeBytes: Long,
) {
    val displayName: String get() = spec?.displayName ?: file.nameWithoutExtension
    val sizeGb: Double get() = sizeBytes / 1_073_741_824.0
}

sealed interface DownloadState {
    data class Running(val downloadedBytes: Long, val totalBytes: Long) : DownloadState {
        val fraction: Float?
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes) else null
    }

    /**
     * Queued or stalled. Previously these were reported as Running, so a download
     * waiting on a network it would never get looked identical to one making
     * progress — the screen just sat there.
     */
    data class Waiting(
        val reason: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : DownloadState

    data class Done(val file: File) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/**
 * Finds, downloads and imports GGUF model files.
 *
 * Downloads go through the system DownloadManager rather than an in-app HTTP
 * request: these files are gigabytes, and the download has to survive the user
 * leaving the app or the screen turning off.
 */
class ModelManager(private val context: Context) {

    private val downloads =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val modelsDir: File
        get() = File(context.getExternalFilesDir(null), MODELS_DIR).apply { mkdirs() }

    fun installed(): List<InstalledModel> =
        modelsDir.listFiles { f -> f.isFile && f.name.endsWith(".gguf") }
            ?.filter { isGguf(it) }
            ?.map { file ->
                InstalledModel(
                    file = file,
                    spec = ModelCatalog.all.firstOrNull { it.fileName == file.name },
                    sizeBytes = file.length(),
                )
            }
            ?.sortedBy { it.displayName }
            .orEmpty()

    fun isInstalled(spec: ModelSpec): Boolean = fileFor(spec).let { it.exists() && isGguf(it) }

    fun fileFor(spec: ModelSpec): File = File(modelsDir, spec.fileName)

    fun usableSpaceBytes(): Long = modelsDir.usableSpace

    fun delete(model: InstalledModel): Boolean = model.file.delete()

    /**
     * Copies a model into Downloads/Jarvis.
     *
     * Models live in the app's external files directory, which Android deletes
     * on uninstall — and from Android 11 no file manager can reach it either, so
     * the file cannot be rescued from outside. A copy in Downloads survives both
     * and can be handed straight back through [import].
     */
    fun exportToDownloads(model: InstalledModel): Result<String> = runCatching {
        val free = Environment.getExternalStorageDirectory().usableSpace
        if (free < model.sizeBytes) {
            error("Storage kam hai — %.1f GB chahiye".format(model.sizeGb))
        }

        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, model.file.name)
            put(MediaStore.Downloads.MIME_TYPE, GGUF_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: error("Downloads mein file nahi banayi ja saki")

        try {
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { "Downloads mein likha nahi ja saka" }
                model.file.inputStream().use { it.copyTo(out) }
            }
        } catch (e: Throwable) {
            // A half-written export is worse than none: it looks importable.
            resolver.delete(uri, null, null)
            throw e
        }

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        "Downloads/$EXPORT_DIR/${model.file.name}"
    }

    fun isGguf(file: File): Boolean = GgufFile.isGguf(file)

    fun startDownload(spec: ModelSpec): Long {
        val target = fileFor(spec)
        if (target.exists()) target.delete()
        val request = DownloadManager.Request(Uri.parse(spec.downloadUrl))
            .setTitle(spec.displayName)
            .setDescription("Jarvis ka dimaag download ho raha hai")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setDestinationInExternalFilesDir(
                context, null, "$MODELS_DIR/${spec.fileName}"
            )
            // Mobile data allowed: a download that silently queues forever
            // waiting for Wi-Fi is worse than one the user chose to pay for.
            // The screen states the size before this is ever called.
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        return downloads.enqueue(request)
    }

    fun cancelDownload(id: Long) {
        downloads.remove(id)
    }

    /** Polls the DownloadManager, ending at the first terminal state. */
    fun observeDownload(id: Long, spec: ModelSpec): Flow<DownloadState> = flow {
        while (true) {
            val query = DownloadManager.Query().setFilterById(id)
            val state = downloads.query(query).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) {
                    return@use DownloadState.Failed("Download record nahi mila")
                }
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )
                val soFar = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                val reason = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                )
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> verifyDownloaded(spec, total)

                    DownloadManager.STATUS_FAILED -> DownloadState.Failed(describeFailure(reason))

                    DownloadManager.STATUS_PAUSED ->
                        DownloadState.Waiting(describePause(reason), soFar, total)

                    DownloadManager.STATUS_PENDING ->
                        DownloadState.Waiting("Shuru hone ka intezaar…", soFar, total)

                    else -> DownloadState.Running(soFar, total)
                }
            }
            emit(state)
            if (state is DownloadState.Done || state is DownloadState.Failed) return@flow
            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * Copies a GGUF the user picked themselves. This is the route that always
     * works: any model, from anywhere, without waiting on a catalog entry.
     */
    fun import(uri: Uri, fileName: String): Result<File> = runCatching {
        val target = File(modelsDir, fileName.ifBlank { "imported.gguf" }.ensureGgufSuffix())
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "File padhi nahi ja saki" }
            target.outputStream().use(input::copyTo)
        }
        if (!isGguf(target)) {
            target.delete()
            error("Yeh GGUF file nahi hai")
        }
        target
    }

    private fun String.ensureGgufSuffix() = if (endsWith(".gguf")) this else "$this.gguf"

    /**
     * A GGUF header only proves the first eight bytes arrived. A download cut
     * short still passes that check, gets listed as installed, and then fails
     * at load time — so compare the size the server reported too.
     */
    private fun verifyDownloaded(spec: ModelSpec, reportedTotal: Long): DownloadState {
        val file = fileFor(spec)
        return when {
            !file.exists() ->
                DownloadState.Failed("Download poora hua par file nahi mili")

            !isGguf(file) -> {
                file.delete()
                DownloadState.Failed("Yeh GGUF file nahi hai — link galat ho sakta hai")
            }

            reportedTotal > 0 && file.length() != reportedTotal -> {
                file.delete()
                DownloadState.Failed(
                    "File adhoori utri (%.2f GB / %.2f GB). Dobara koshish karein.".format(
                        file.length() / 1_073_741_824.0,
                        reportedTotal / 1_073_741_824.0,
                    )
                )
            }

            else -> DownloadState.Done(file)
        }
    }

    private fun describePause(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Network ka intezaar hai"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "WiFi ka intezaar hai"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Dobara koshish kar raha hai…"
        else -> "Ruka hua hai (code $reason)"
    }

    private fun describeFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Phone mein jagah kam hai"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage nahi mila"
        DownloadManager.ERROR_CANNOT_RESUME -> "Download resume nahi hua, dobara shuru karein"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network toot gaya"
        DownloadManager.ERROR_FILE_ERROR -> "File likhi nahi ja saki"
        in 400..599 -> "Server ne mana kiya (HTTP $reason). Link badal gaya ho sakta hai — " +
            "model khud download karke import kar lein."

        else -> "Download fail hua (code $reason)"
    }

    private companion object {
        const val MODELS_DIR = "models"
        const val EXPORT_DIR = "Jarvis"
        const val GGUF_MIME = "application/octet-stream"
        const val POLL_INTERVAL_MS = 500L
    }
}
