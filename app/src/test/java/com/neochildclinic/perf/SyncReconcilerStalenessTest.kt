package com.neochildclinic.perf

import com.neochildclinic.app.sync.FULL_REFRESH_STALENESS_MS
import com.neochildclinic.app.sync.shouldRunFullRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SY-02 guard: the full 13-table inbound pull must be skipped while the last successful
 * pass is fresh, and must still run for a stale pass or on first-ever launch. The gate is a
 * pure function (like the SyncWorker policy) so it is testable without an Android runtime;
 * outbound push and the offline->online trigger are intentionally left unchanged.
 */
class SyncReconcilerStalenessTest {

    @Test
    fun warmRecentSyncSkipsFullPassWhileStaleOneRunsIt() {
        val now = 1_800_000_000_000L
        val threshold = FULL_REFRESH_STALENESS_MS

        // First-ever launch: no stored timestamp -> must pull.
        assertTrue(shouldRunFullRefresh(0L, now, threshold))
        // Warm: pulled a minute ago -> skip the expensive full pass.
        assertFalse(shouldRunFullRefresh(now - 60_000L, now, threshold))
        // Exactly at the staleness boundary -> pull.
        assertTrue(shouldRunFullRefresh(now - threshold, now, threshold))
        // Stale: pulled 20 minutes ago -> pull.
        assertTrue(shouldRunFullRefresh(now - 20 * 60 * 1000L, now, threshold))
    }
}
