package com.neochildclinic.updatefix

import com.neochildclinic.feature.update.data.AppUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RE-03 regression. The installed build derives its versionCode from versionName with the
 * same major*1_000_000 + minor*1_000 + patch formula the updater derives from the release
 * tag. Before the fix, "0.6" was versionCode 4 in the manifest but 6000 by tag, so the
 * updater offered the just-installed release back to the user on the next check.
 */
class VersionCodeAlignmentTest {

    @Test
    fun tagDerivesTheSameCodeTheBuildDerivesFromVersionName() {
        // appVersionName = "0.6" -> versionCode 6000 in app/build.gradle.kts.
        assertEquals(6000L, AppUpdateManager.versionCodeFromTag("0.6"))
        assertEquals(6000L, AppUpdateManager.versionCodeFromTag("v0.6"))
        assertEquals(6000L, AppUpdateManager.versionCodeFromTag("v0.6.0"))
        assertEquals(1_002_003L, AppUpdateManager.versionCodeFromTag("v1.2.3"))
    }

    @Test
    fun firstCheckOffersUpdate_secondCheckAfterInstallReturnsNull() {
        val remoteCode = AppUpdateManager.versionCodeFromTag("v0.6")!! // 6000
        val installedBefore = 4L
        val installedAfter = remoteCode // what the installed APK now reports

        assertTrue(AppUpdateManager.shouldOfferUpdate(remoteCode, installedBefore))
        // parseRelease() returns null on this guard, so re-checking after install is a no-op.
        assertFalse(AppUpdateManager.shouldOfferUpdate(remoteCode, installedAfter))
    }
}
