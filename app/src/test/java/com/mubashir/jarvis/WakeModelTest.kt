package com.mubashir.jarvis

import com.mubashir.jarvis.voice.WakeModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeModelTest {

    @Test
    fun `a complete model has the directories the recogniser reads`() {
        assertTrue(
            WakeModel.looksComplete(listOf("am", "conf", "graph", "ivector", "README")),
        )
    }

    @Test
    fun `a download that died halfway is not complete`() {
        // The failure this exists to stop: a partial unpack looks like a
        // directory, gets loaded, and fails inside native code with nothing
        // readable to show the user.
        assertFalse(WakeModel.looksComplete(listOf("am")))
        assertFalse(WakeModel.looksComplete(listOf("am", "conf")))
        assertFalse(WakeModel.looksComplete(emptyList()))
    }

    @Test
    fun `the expected directory is preferred when several are complete`() {
        val root = WakeModel.modelRoot(
            mapOf(
                "something-else" to listOf("am", "conf", "graph"),
                WakeModel.DIR_NAME to listOf("am", "conf", "graph"),
            ),
        )
        assertEquals(WakeModel.DIR_NAME, root)
    }

    @Test
    fun `a differently named model is still found`() {
        val root = WakeModel.modelRoot(
            mapOf("vosk-model-small-en-us-9.9" to listOf("am", "conf", "graph")),
        )
        assertEquals("vosk-model-small-en-us-9.9", root)
    }

    @Test
    fun `an incomplete directory is not offered as the model`() {
        assertNull(WakeModel.modelRoot(mapOf(WakeModel.DIR_NAME to listOf("am"))))
        assertNull(WakeModel.modelRoot(emptyMap()))
    }

    @Test
    fun `an entry that climbs out of the unpack directory is refused`() {
        assertNull(WakeModel.safeEntryName("../settings.xml"))
        assertNull(WakeModel.safeEntryName("model/../../shared_prefs/settings.xml"))
        assertNull(WakeModel.safeEntryName("..\\..\\settings.xml"))
        assertNull(WakeModel.safeEntryName("/etc/passwd"))
        assertNull(WakeModel.safeEntryName(""))
        assertNull(WakeModel.safeEntryName("   "))
    }

    @Test
    fun `an ordinary entry keeps its path`() {
        assertEquals("model/am/final.mdl", WakeModel.safeEntryName("model/am/final.mdl"))
        assertEquals("model/am/final.mdl", WakeModel.safeEntryName("./model/am/final.mdl"))
        assertEquals("model/conf", WakeModel.safeEntryName("model/conf/"))
        assertEquals("model/am/final.mdl", WakeModel.safeEntryName("model\\am\\final.mdl"))
    }

    @Test
    fun `the download link is the small English model`() {
        assertTrue(WakeModel.URL.startsWith("https://"))
        assertTrue(WakeModel.URL.endsWith(".zip"))
        // The name in the URL is what the unpacked directory is called, so they
        // cannot drift apart without the model becoming unfindable.
        assertTrue(WakeModel.URL.contains(WakeModel.DIR_NAME))
    }
}
