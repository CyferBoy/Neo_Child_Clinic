package com.neochildclinic.uifix

import com.neochildclinic.feature.statistics.presentation.FullReportUiState
import com.neochildclinic.feature.statistics.presentation.VaccineDetailUiState
import com.neochildclinic.feature.statistics.presentation.mergeFullReportState
import com.neochildclinic.feature.statistics.presentation.mergeVaccineDetailState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the refresh-spinner / error-message wipe: a repository emission must replace
 * only the loaded data, never the transient flags owned by refresh() and error handling.
 */
class RefreshStateRetentionTest {

    @Test
    fun `refresh flag and error message survive a fresh data emission`() {
        // AD-01: collector used to replace the whole state, clearing isRefreshing/errorMessage.
        val currentReport = FullReportUiState(
            isRefreshing = true,
            errorMessage = "sync failed",
            periodLabel = "stale"
        )
        val freshReport = FullReportUiState(
            isLoading = false,
            periodLabel = "fresh"
        )
        val mergedReport = mergeFullReportState(currentReport, freshReport)

        assertEquals("fresh", mergedReport.periodLabel)
        assertTrue("isRefreshing must survive the report emission", mergedReport.isRefreshing)
        assertEquals("sync failed", mergedReport.errorMessage)

        // AD-02: collector used to clear isRefreshing, so pull-to-refresh never spun.
        val currentDetail = VaccineDetailUiState(isRefreshing = true)
        val freshDetail = VaccineDetailUiState(isLoading = false, brandName = "Covishield")
        val mergedDetail = mergeVaccineDetailState(currentDetail, freshDetail)

        assertEquals("Covishield", mergedDetail.brandName)
        assertTrue("isRefreshing must survive the vaccine detail emission", mergedDetail.isRefreshing)
    }
}
