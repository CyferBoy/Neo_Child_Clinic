package com.neochildclinic.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionUtilsTest {

    @Test
    fun sameVersionReturnsFalse() {
        assertFalse(VersionUtils.isNewerVersion("0.6", "0.6"))
        assertFalse(VersionUtils.isNewerVersion("0.6", "v0.6"))
        assertFalse(VersionUtils.isNewerVersion("v0.6", "0.6"))
        assertFalse(VersionUtils.isNewerVersion("0.6.0", "0.6"))
        assertFalse(VersionUtils.isNewerVersion("0.6", "0.6.0"))
        assertFalse(VersionUtils.isNewerVersion("0.6-debug", "0.6"))
        assertFalse(VersionUtils.isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun olderVersionReturnsFalse() {
        assertFalse(VersionUtils.isNewerVersion("0.6", "0.5"))
        assertFalse(VersionUtils.isNewerVersion("0.6", "0.5.1"))
        assertFalse(VersionUtils.isNewerVersion("0.6", "0.5.9"))
        assertFalse(VersionUtils.isNewerVersion("1.0.0", "0.9.9"))
        assertFalse(VersionUtils.isNewerVersion("0.6.2", "0.6.1"))
    }

    @Test
    fun newerVersionReturnsTrue() {
        assertTrue(VersionUtils.isNewerVersion("0.5.1", "0.6"))
        assertTrue(VersionUtils.isNewerVersion("0.6", "0.6.1"))
        assertTrue(VersionUtils.isNewerVersion("0.6", "0.7"))
        assertTrue(VersionUtils.isNewerVersion("0.6", "1.0.0"))
        assertTrue(VersionUtils.isNewerVersion("0.6-debug", "0.6.1"))
        assertTrue(VersionUtils.isNewerVersion("0.6", "v0.6.1"))
    }
}
