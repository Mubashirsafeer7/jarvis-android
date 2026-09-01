package com.mubashir.jarvis.model

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.mubashir.jarvis.R
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

    /**
     * External files when the volume is mounted, internal storage otherwise.
     * getExternalFilesDir returns null on an unmounted volume, which used to
     * yield the path "/models" — unwritable, zero free space, and so a permanent
     * "not enough space" for a phone that had plenty.
     */
    val modelsDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, MODELS_DIR)
            .apply { mkdirs() }

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
            error(context.getString(R.string.error_needs_space, model.sizeGb))
        }

        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, model.file.name)
            put(MediaStore.Downloads.MIME_TYPE, GGUF_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            ?: error(context.getString(R.string.export_could_not_create))

        try {
            resolver.openOutputStream(uri).use { out ->
                requireNotNull(out) { context.getString(R.string.export_could_not_write) }
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
        fileFor(spec).delete()
        partFor(spec).delete()
        val request = DownloadManager.Request(Uri.parse(spec.downloadUrl))
            .setTitle(spec.displayName)
            .setDescription(context.getString(R.string.download_notification))
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            // Downloaded under a name installed() ignores, then renamed once it
            // has been checked. Written straight to its final name, a download
            // still in flight was listed as an installed model with a working
            // Load button, and loading it handed llama.cpp a truncated file.
            .setDestinationInExternalFilesDir(
                context, null, "$MODELS_DIR/${spec.fileName}$PART_SUFFIX"
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
                    return@use DownloadState.Failed(context.getString(R.string.download_record_missing))
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
                val localUri = cursor.getString(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                )
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> verifyDownloaded(spec, total, localUri)

                    DownloadManager.STATUS_FAILED -> DownloadState.Failed(describeFailure(reason))

                    DownloadManager.STATUS_PAUSED ->
                        DownloadState.Waiting(describePause(reason), soFar, total)

                    DownloadManager.STATUS_PENDING ->
                        DownloadState.Waiting(context.getString(R.string.download_pending), soFar, total)

                    else -> DownloadState.Running(soFar, total)
                }
            }
            emit(state)
            if (state is DownloadState.Done || state is DownloadState.Failed) return@flow
            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO) // a provider query and a file check, twice a second

    /**
     * Copies a GGUF the user picked themselves. This is the route that always
     * works: any model, from anywhere, without waiting on a catalog entry.
     */
    fun import(uri: Uri, fileName: String): Result<File> = runCatching {
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { "imported.gguf" }
            .ensureGgufSuffix()
        val target = File(modelsDir, safeName)
        // Copied beside the real name and renamed only once it checks out. A copy
        // that failed part way used to be left on disk — gigabytes that then
        // passed the header check and were offered as a model. Writing straight
        // to the target was worse still when that target was the model currently
        // loaded: llama.cpp has it mapped, and truncating it under itself is a
        // crash rather than an error.
        val staging = File(modelsDir, "$safeName$PART_SUFFIX")
        staging.delete()
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { context.getString(R.string.import_unreadable) }
                staging.outputStream().use(input::copyTo)
            }
            if (!isGguf(staging)) error(context.getString(R.string.import_not_gguf))
            target.delete()
            if (!staging.renameTo(target)) error(context.getString(R.string.import_failed))
            target
        } catch (e: Throwable) {
            staging.delete()
            throw e
        }
    }

    private fun String.ensureGgufSuffix() = if (endsWith(".gguf")) this else "$this.gguf"

    /**
     * A GGUF header only proves the first eight bytes arrived. A download cut
     * short still passes that check, gets listed as installed, and then fails
     * at load time — so compare the size the server reported too.
     */
    private fun verifyDownloaded(
        spec: ModelSpec,
        reportedTotal: Long,
        localUri: String?,
    ): DownloadState {
        // Where the download manager says it put the file, not where it was
        // asked to. It appends -1, -2 on a name collision, and assuming the
        // requested path meant a perfectly good download reported itself missing.
        val file = localUri?.let { runCatching { Uri.parse(it).path?.let { path -> File(path) } }.getOrNull() }
            ?.takeIf { it.exists() }
            ?: partFor(spec).takeIf { it.exists() }
            ?: return DownloadState.Failed(context.getString(R.string.download_finished_no_file))

        if (!isGguf(file)) {
            file.delete()
            return DownloadState.Failed(context.getString(R.string.download_not_gguf))
        }
        if (reportedTotal > 0 && file.length() != reportedTotal) {
            val short = context.getString(
                R.string.download_truncated,
                file.length() / BYTES_PER_GB,
                reportedTotal / BYTES_PER_GB,
            )
            file.delete()
            return DownloadState.Failed(short)
        }

        val target = fileFor(spec)
        target.delete()
        if (!file.renameTo(target)) {
            file.delete()
            return DownloadState.Failed(context.getString(R.string.download_could_not_finish))
        }
        return DownloadState.Done(target)
    }

    private fun partFor(spec: ModelSpec) = File(modelsDir, "${spec.fileName}$PART_SUFFIX")

    private fun describePause(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> context.getString(R.string.paused_network)
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> context.getString(R.string.paused_wifi)
        DownloadManager.PAUSED_WAITING_TO_RETRY -> context.getString(R.string.paused_retry)
        else -> context.getString(R.string.paused_unknown, reason)
    }

    private fun describeFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> context.getString(R.string.failed_space)
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> context.getString(R.string.failed_no_storage)
        DownloadManager.ERROR_CANNOT_RESUME -> context.getString(R.string.failed_cannot_resume)
        DownloadManager.ERROR_HTTP_DATA_ERROR -> context.getString(R.string.failed_network)
        DownloadManager.ERROR_FILE_ERROR -> context.getString(R.string.failed_file)
        in 400..599 -> context.getString(R.string.failed_http, reason)

        else -> context.getString(R.string.failed_unknown, reason)
    }

    private companion object {
        const val MODELS_DIR = "models"
        const val EXPORT_DIR = "Jarvis"
        const val GGUF_MIME = "application/octet-stream"
        const val POLL_INTERVAL_MS = 500L
        const val PART_SUFFIX = ".part"
        const val BYTES_PER_GB = 1_073_741_824.0
    }
}
