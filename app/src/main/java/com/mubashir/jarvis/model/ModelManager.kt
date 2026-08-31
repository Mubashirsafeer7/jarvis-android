package com.mubashir.jarvis.model

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
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
            .setAllowedOverMetered(false)
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
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = fileFor(spec)
                        if (isGguf(file)) {
                            DownloadState.Done(file)
                        } else {
                            file.delete()
                            DownloadState.Failed(
                                "File poori nahi utri ya GGUF nahi hai. Dobara koshish karein."
                            )
                        }
                    }

                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        )
                        DownloadState.Failed(describeFailure(reason))
                    }

                    else -> DownloadState.Running(soFar, total)
                }
            }
            emit(state)
            if (state !is DownloadState.Running) return@flow
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
        const val POLL_INTERVAL_MS = 500L
    }
}
