package com.neochildclinic.authfix2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SE-01 regression: the 24h offline-unlock stamp may only be refreshed by a real
 * authenticated round-trip (profile refresh), never by a cached StateFlow status
 * that merely coincides with Android reporting a working Wi-Fi connection.
 */
class OfflineStampRefreshSourceTest {

    @Test
    fun stampIsRefreshedOnlyAfterProfileRefresh() {
        val repo = File(mainRoot(), "feature/auth/data/repository/AuthRepositoryImpl.kt").readText()
        val vm = File(mainRoot(), "feature/auth/presentation/AuthViewModel.kt").readText()

        val awaitBody = functionBody(repo, "awaitResolvedSessionStatus")
        assertFalse(
            "a cached session status must not refresh the offline stamp",
            awaitBody.contains("recordOnlineVerification")
        )

        val fetchBody = functionBody(vm, "fetchProfile")
        val refreshIndex = fetchBody.indexOf("profileRepository.refreshProfiles()")
        val stampIndex = fetchBody.indexOf("recordOnlineVerification()")
        assertTrue("fetchProfile must call refreshProfiles()", refreshIndex >= 0)
        assertTrue("fetchProfile must refresh the offline stamp", stampIndex >= 0)
        assertTrue(
            "the stamp must be refreshed after the successful authenticated round-trip, not before",
            stampIndex > refreshIndex
        )
    }

    /** Body of the named function, from its signature through its matching closing brace. */
    private fun functionBody(source: String, name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("missing function $name", start >= 0)
        val open = source.indexOf('{', start)
        assertTrue("missing body for $name", open >= 0)
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
        }
        error("unbalanced braces in $name")
    }

    private fun mainRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(dir, "app/src/main/java/com/neochildclinic")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile ?: return dir
        }
        error("Could not locate app/src/main/java/com/neochildclinic")
    }
}
