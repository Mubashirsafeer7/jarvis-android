package com.mubashir.jarvis

import com.mubashir.jarvis.update.AvailableUpdate
import com.mubashir.jarvis.update.UpdateCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    private fun release(
        tag: String = "v0.3.42",
        assetName: String = "jarvis-42.apk",
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = """
        {
          "tag_name": "$tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "body": "What changed",
          "assets": [
            {"name": "$assetName",
             "browser_download_url": "https://example.test/$assetName",
             "size": 62211232}
          ]
        }
    """.trimIndent()

    @Test
    fun `the tag carries the version code`() {
        assertEquals(42L, UpdateCheck.versionCodeOf("v0.3.42"))
        assertEquals(42L, UpdateCheck.versionCodeOf("0.3.42"))
    }

    @Test
    fun `a tag that does not parse is no update, not version zero`() {
        // A hand-made tag must never come out looking newer or older by accident.
        assertNull(UpdateCheck.versionCodeOf("nightly"))
        assertNull(UpdateCheck.versionCodeOf("v"))
        assertNull(UpdateCheck.versionCodeOf(""))
        assertNull(UpdateCheck.versionCodeOf("v0.3.0"))
    }

    @Test
    fun `a release yields its apk`() {
        val update = UpdateCheck.parseLatest(release())!!
        assertEquals(42L, update.versionCode)
        assertEquals("0.3.42", update.versionName)
        assertEquals("https://example.test/jarvis-42.apk", update.apkUrl)
        assertEquals(62211232L, update.sizeBytes)
        assertEquals("What changed", update.notes)
    }

    @Test
    fun `drafts and pre-releases are not offered`() {
        assertNull(UpdateCheck.parseLatest(release(draft = true)))
        assertNull(UpdateCheck.parseLatest(release(prerelease = true)))
    }

    @Test
    fun `a release with no apk attached is not an update`() {
        // The build can fail after the release is created.
        assertNull(UpdateCheck.parseLatest(release(assetName = "notes.txt")))
    }

    @Test
    fun `rubbish in gives nothing out rather than throwing`() {
        assertNull(UpdateCheck.parseLatest(""))
        assertNull(UpdateCheck.parseLatest("<html>404</html>"))
        assertNull(UpdateCheck.parseLatest("{}"))
    }

    @Test
    fun `only a strictly newer build is offered`() {
        val update = UpdateCheck.parseLatest(release())!!
        assertTrue(UpdateCheck.isNewer(41L, update))
        assertFalse("the installed build must not be offered", UpdateCheck.isNewer(42L, update))
        assertFalse("an older release must not be offered", UpdateCheck.isNewer(43L, update))
    }

    @Test
    fun `an update is compared by code, not by name`() {
        val ten = AvailableUpdate(10L, "0.3.10", "u", 1L, "")
        // 0.3.9 sorts after 0.3.10 as text; as numbers it does not.
        assertTrue(UpdateCheck.isNewer(9L, ten))
    }
}
