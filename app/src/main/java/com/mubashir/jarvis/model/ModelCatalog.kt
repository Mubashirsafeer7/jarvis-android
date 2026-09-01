package com.mubashir.jarvis.model

import com.mubashir.jarvis.DeviceCapabilities

/**
 * Which model fills which role. Voice commands need an answer to start in about
 * a second, while a question worth thinking about can afford to be slow, and a 12 GB
 * phone has room for both — so the two roles are separate models rather than one
 * compromise between them.
 */
enum class ModelRole { FAST, DEEP }

data class ModelSpec(
    val id: String,
    val displayName: String,
    val role: ModelRole,
    val fileName: String,
    val downloadUrl: String,
    /** For the free-space check and the download UI; the real size comes from the server. */
    val approxBytes: Long,
    /**
     * Physical RAM this needs — weights plus the KV cache plus whatever Android
     * itself is holding, not just the file size. Virtual/swap RAM does not help
     * a memory-mapped model.
     */
    val minRamGb: Double,
    val notes: String,
)

object ModelCatalog {

    private const val GB = 1_073_741_824L

    val all = listOf(
        ModelSpec(
            id = "qwen2.5-1.5b-instruct-q4km",
            displayName = "Qwen2.5 1.5B Instruct",
            role = ModelRole.FAST,
            fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/" +
                "resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf?download=true",
            approxBytes = (1.1 * GB).toLong(),
            minRamGb = 3.5,
            notes = "Fastest. Fine for commands and short exchanges.",
        ),
        ModelSpec(
            id = "qwen2.5-3b-instruct-q4km",
            displayName = "Qwen2.5 3B Instruct",
            role = ModelRole.FAST,
            fileName = "Qwen2.5-3B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-3B-Instruct-GGUF/" +
                "resolve/main/Qwen2.5-3B-Instruct-Q4_K_M.gguf?download=true",
            approxBytes = (2.0 * GB).toLong(),
            // 2 GB of weights and an 8k KV cache leave nothing over on a 6 GB
            // phone once Android has taken its share, so this needs 8 GB class.
            minRamGb = 7.0,
            notes = "The best balance for everyday use — quick, and it understands.",
        ),
        ModelSpec(
            id = "qwen2.5-7b-instruct-q4km",
            displayName = "Qwen2.5 7B Instruct",
            role = ModelRole.DEEP,
            fileName = "Qwen2.5-7B-Instruct-Q4_K_M.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Qwen2.5-7B-Instruct-GGUF/" +
                "resolve/main/Qwen2.5-7B-Instruct-Q4_K_M.gguf?download=true",
            approxBytes = (4.7 * GB).toLong(),
            minRamGb = 9.0,
            notes = "For longer questions and explanations. Slow for voice.",
        ),
    )

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }

    /** Models this phone has the RAM to run, best first within each role. */
    fun runnableOn(caps: DeviceCapabilities): List<ModelSpec> =
        all.filter { caps.totalRamGb >= it.minRamGb }

    /** What to load for everyday use on this phone: the largest FAST model that fits. */
    fun defaultFastFor(caps: DeviceCapabilities): ModelSpec? =
        all.filter { it.role == ModelRole.FAST && caps.totalRamGb >= it.minRamGb }
            .maxByOrNull { it.approxBytes }
}
