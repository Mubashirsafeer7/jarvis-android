package com.mubashir.jarvis

import com.mubashir.jarvis.model.ModelCatalog
import com.mubashir.jarvis.model.ModelRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    private fun caps(ramGb: Double) = DeviceCapabilities(
        totalRamBytes = (ramGb * 1_073_741_824.0).toLong(),
        availableRamBytes = 0,
        cpuCores = 8,
        abis = listOf("arm64-v8a"),
        androidSdk = 36,
        deviceName = "test",
    )

    @Test
    fun `12GB phone gets the 3B model for everyday use`() {
        // The realme 15 this is built for reports ~11.3 GB of its 12 GB.
        assertEquals("qwen2.5-3b-instruct-q4km", ModelCatalog.defaultFastFor(caps(11.3))?.id)
    }

    @Test
    fun `12GB phone can also run the deep model`() {
        val ids = ModelCatalog.runnableOn(caps(11.3)).map { it.id }
        assertTrue("7B should fit on 12GB", ids.contains("qwen2.5-7b-instruct-q4km"))
    }

    @Test
    fun `6GB phone falls back to the small model and cannot run 7B`() {
        assertEquals("qwen2.5-1.5b-instruct-q4km", ModelCatalog.defaultFastFor(caps(5.7))?.id)
        assertTrue(ModelCatalog.runnableOn(caps(5.7)).none { it.role == ModelRole.DEEP })
    }

    @Test
    fun `a phone too small for anything gets nothing rather than a bad default`() {
        assertNull(ModelCatalog.defaultFastFor(caps(2.0)))
        assertTrue(ModelCatalog.runnableOn(caps(2.0)).isEmpty())
    }

    @Test
    fun `every catalog entry has a distinct id and file name`() {
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.toSet().size)
        assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.fileName }.toSet().size)
    }

    @Test
    fun `download urls point at gguf files over https`() {
        ModelCatalog.all.forEach { spec ->
            assertTrue(spec.id, spec.downloadUrl.startsWith("https://"))
            assertTrue(spec.id, spec.downloadUrl.contains(".gguf"))
        }
    }

    @Test
    fun `byId round trips`() {
        ModelCatalog.all.forEach { assertEquals(it, ModelCatalog.byId(it.id)) }
        assertNull(ModelCatalog.byId("nope"))
    }
}
