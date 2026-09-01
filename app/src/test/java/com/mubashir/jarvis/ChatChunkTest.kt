package com.mubashir.jarvis

import com.mubashir.jarvis.llm.ChatChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatChunkTest {

    @Test
    fun `a content chunk yields its text`() {
        val line = """data: {"choices":[{"delta":{"content":"Hello"}}]}"""
        assertEquals("Hello", ChatChunk.textOf(line))
    }

    @Test
    fun `the end of the stream is the end`() {
        assertNull(ChatChunk.textOf("data: [DONE]"))
        assertNull(ChatChunk.textOf("data:[DONE]"))
    }

    @Test
    fun `the first chunk carries a role and no content`() {
        // Normal, and must not be mistaken for the end.
        val line = """data: {"choices":[{"delta":{"role":"assistant"}}]}"""
        assertEquals("", ChatChunk.textOf(line))
    }

    @Test
    fun `keep-alives and blank lines carry nothing and end nothing`() {
        assertEquals("", ChatChunk.textOf(""))
        assertEquals("", ChatChunk.textOf("   "))
        assertEquals("", ChatChunk.textOf(": ping"))
        assertEquals("", ChatChunk.textOf("data:"))
    }

    @Test
    fun `a non-streaming answer is read too`() {
        val line = """data: {"choices":[{"message":{"content":"Whole answer"}}]}"""
        assertEquals("Whole answer", ChatChunk.textOf(line))
    }

    @Test
    fun `whitespace in the content survives`() {
        // Dropping a leading space glues words together across chunks.
        val line = """data: {"choices":[{"delta":{"content":" world"}}]}"""
        assertEquals(" world", ChatChunk.textOf(line))
    }

    @Test
    fun `rubbish is skipped rather than ending the answer`() {
        // A malformed line mid-stream must not truncate a good reply.
        assertEquals("", ChatChunk.textOf("data: {not json"))
        assertEquals("", ChatChunk.textOf("""data: {"choices":[]}"""))
        assertEquals("", ChatChunk.textOf("""data: {}"""))
    }
}
