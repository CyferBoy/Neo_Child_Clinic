package com.neochildclinic.feature.update.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateManagerTest {

    private val sampleHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun matchesStandardSha256Line() {
        val input = "sha256: $sampleHash"
        val match = AppUpdateManager.RE_SHA256_LINE.find(input)
        assertEquals(sampleHash, match?.groupValues?.get(1)?.lowercase())
    }

    @Test
    fun matchesHyphenatedSha256Line() {
        val input = "SHA-256: $sampleHash"
        val match = AppUpdateManager.RE_SHA256_LINE.find(input)
        assertEquals(sampleHash, match?.groupValues?.get(1)?.lowercase())
    }

    @Test
    fun matchesApkHyphenatedSha256Line() {
        val input = "apk-sha-256: $sampleHash"
        val match = AppUpdateManager.RE_SHA256_LINE.find(input)
        assertEquals(sampleHash, match?.groupValues?.get(1)?.lowercase())
    }

    @Test
    fun matchesMarkdownBoldSha256() {
        val input = "**SHA-256**: $sampleHash"
        val match = AppUpdateManager.RE_SHA256_LINE.find(input)
        assertEquals(sampleHash, match?.groupValues?.get(1)?.lowercase())
    }

    @Test
    fun matchesMarkdownBoldInsideColonSha256() {
        val input = "**SHA-256:** $sampleHash"
        val match = AppUpdateManager.RE_SHA256_LINE.find(input)
        assertEquals(sampleHash, match?.groupValues?.get(1)?.lowercase())
    }

    @Test
    fun matchesMarkdownCodeBackticksSha256() {
        val input = "**SHA-256**: `$sampleHash`"
        val match = AppUpdateManager.RE_SHA256_LINE.find(input)
        assertEquals(sampleHash, match?.groupValues?.get(1)?.lowercase())
    }

    @Test
    fun rejectsInvalidHashLength() {
        val input = "SHA-256: 0123456789abcdef"
        val match = AppUpdateManager.RE_SHA256_LINE.find(input)
        assertNull(match)
    }
}
