package com.mubashir.jarvis

import com.mubashir.jarvis.ui.prettyModelName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelNameTest {

    @Test
    fun `the catalog model reads as a name, not a file`() {
        assertEquals(
            "Qwen2.5 3B Instruct",
            prettyModelName("Qwen2.5-3B-Instruct-Q4_K_M.gguf"),
        )
    }

    @Test
    fun `other quantisations are dropped too`() {
        assertEquals("Llama 3.2 3B Instruct", prettyModelName("Llama-3.2-3B-Instruct-Q5_K_S.gguf"))
        assertEquals("Phi 4 mini", prettyModelName("Phi-4-mini-f16.gguf"))
    }

    @Test
    fun `a name with no quantisation survives intact`() {
        assertEquals("my model", prettyModelName("my-model.gguf"))
    }

    @Test
    fun `an unexpected name is shown rather than blanked`() {
        // Better a raw name than an empty header.
        assertTrue(prettyModelName(".gguf").isNotBlank())
        assertTrue(prettyModelName("x").isNotBlank())
        assertEquals("weird name", prettyModelName("weird_name"))
    }
}
