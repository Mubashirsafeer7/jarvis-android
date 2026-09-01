package com.mubashir.jarvis.ui

/**
 * A model's name as a person would write it.
 *
 * The header showed the file name verbatim — "Qwen2.5-3B-Instruct-Q4_K_M.gguf",
 * 38 monospace characters in about 105dp of space, which both wrapped and told
 * the user nothing they wanted to know.
 */
fun prettyModelName(fileName: String): String {
    val stem = fileName.removeSuffix(".gguf").removeSuffix(".GGUF")
    if (stem.isBlank()) return fileName
    return stem
        // Quantisation suffixes are the file's business, not the reader's.
        .replace(Regex("[-_.]([Qq]\\d[-_.]?[A-Za-z0-9_]*|[Ff]16|[Ff]32|[Bb][Ff]16)$"), "")
        .replace('-', ' ')
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { fileName }
}
