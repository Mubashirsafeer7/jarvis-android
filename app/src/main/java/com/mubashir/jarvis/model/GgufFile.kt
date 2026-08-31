package com.mubashir.jarvis.model

import java.io.File
import java.io.RandomAccessFile

/**
 * A GGUF file starts with the magic "GGUF" followed by a little-endian version
 * word. Checking that catches the failures that actually happen — a truncated
 * download, or an HTML error page saved under a .gguf name — before llama.cpp
 * loads it and takes the process down.
 */
object GgufFile {

    private val MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46) // "GGUF"
    private const val MAX_VERSION = 10
    private const val HEADER_BYTES = 8

    fun isGguf(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() < HEADER_BYTES) return false
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4).also(raf::readFully)
            if (!magic.contentEquals(MAGIC)) return false
            readLittleEndianInt(ByteArray(4).also(raf::readFully)) in 1..MAX_VERSION
        }
    }.getOrDefault(false)

    private fun readLittleEndianInt(b: ByteArray): Int =
        (b[0].toInt() and 0xFF) or
            ((b[1].toInt() and 0xFF) shl 8) or
            ((b[2].toInt() and 0xFF) shl 16) or
            ((b[3].toInt() and 0xFF) shl 24)
}
