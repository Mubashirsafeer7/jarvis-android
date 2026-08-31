package com.mubashir.jarvis

import com.mubashir.jarvis.model.GgufFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GgufFileTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun write(name: String, bytes: ByteArray): File =
        temp.newFile(name).apply { writeBytes(bytes) }

    private fun header(magic: String, version: Int): ByteArray =
        magic.toByteArray() + byteArrayOf(
            version.toByte(),
            (version shr 8).toByte(),
            (version shr 16).toByte(),
            (version shr 24).toByte(),
        )

    @Test
    fun `accepts a real gguf header`() {
        assertTrue(GgufFile.isGguf(write("ok.gguf", header("GGUF", 3) + ByteArray(64))))
    }

    @Test
    fun `rejects an html error page saved as gguf`() {
        // What a broken download link actually leaves on disk.
        val html = "<!DOCTYPE html><html><body>404</body></html>".toByteArray()
        assertFalse(GgufFile.isGguf(write("nope.gguf", html)))
    }

    @Test
    fun `rejects a truncated download`() {
        assertFalse(GgufFile.isGguf(write("short.gguf", "GGU".toByteArray())))
    }

    @Test
    fun `rejects an empty file`() {
        assertFalse(GgufFile.isGguf(temp.newFile("empty.gguf")))
    }

    @Test
    fun `rejects an implausible version`() {
        assertFalse(GgufFile.isGguf(write("future.gguf", header("GGUF", 9999) + ByteArray(64))))
    }

    @Test
    fun `rejects a missing file`() {
        assertFalse(GgufFile.isGguf(File(temp.root, "does-not-exist.gguf")))
    }

    @Test
    fun `rejects a directory`() {
        assertFalse(GgufFile.isGguf(temp.newFolder("dir.gguf")))
    }
}
