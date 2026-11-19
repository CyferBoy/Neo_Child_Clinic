package com.neochildclinic.finalfix1

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SE-01: every path that unlocks app-lock must pass the 24h offline stamp gate, not just the
 * account-password path. MainActivity is an Activity (not JVM-instantiable here), so this
 * suite checks the invariant structurally - the same source-inspection approach this repo
 * already uses for its import-boundary guards.
 */
class UnlockGateAllBranchesTest {

    @Test
    fun `all unlock branches are gated by the offline stamp check`() {
        val source = mainActivitySource().readLines()
        val unlockSites = source.mapIndexedNotNull { index, line ->
            if (isUnlockCall(line)) index to line.trim() else null
        }
        assertTrue("expected all three unlock call sites in MainActivity", unlockSites.size >= 3)

        unlockSites.forEach { (index, call) ->
            val preceding = source.subList((index - 5).coerceAtLeast(0), index).joinToString("\n")
            assertTrue(
                "unlock call is not guarded by the offline gate: $call\npreceding:\n$preceding",
                "offlineUnlockGateAllows()" in preceding
            )
        }

        // The shared gate must itself delegate to the 24h stamp check.
        assertTrue(
            "gate must consult OfflineAuthManager.isOfflineUnlockAllowed()",
            source.joinToString("\n").contains("offlineAuthManager.isOfflineUnlockAllowed()")
        )
    }

    private fun isUnlockCall(line: String): Boolean =
        "BiometricLockManager.unlockAfterKeystoreVerification()" in line ||
            "BiometricLockManager.unlockBecauseProtectionIsDisabled()" in line

    private fun mainActivitySource(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(dir, "app/src/main/java/com/neochildclinic/app/MainActivity.kt")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: return dir
        }
        error("Could not locate MainActivity.kt from ${System.getProperty("user.dir")}")
    }
}
