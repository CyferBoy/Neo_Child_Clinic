package com.neochildclinic.feature.statistics.domain

import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import org.junit.Assert.assertEquals
import org.junit.Test

class VisitTypeStatsTest {

    private fun visit(id: String = "v1", patientId: String = "pat", type: String = "VACCINATION", status: ReminderStatus = ReminderStatus.ACTIVE) = Vaccination(
        id = id,
        patientId = patientId,
        dateGiven = "2026-09-01",
        items = emptyList(),
        status = status,
        source = "CLINIC",
        visitType = type
    )

    @Test
    fun `fresh consultation counts even with ACTIVE status`() {
        val stats = StatisticsUtils.visitTypeStats(listOf(visit(type = "CONSULTATION")))
        assertEquals(1, stats.totalConsultation)
        assertEquals(1, stats.consultedPatients)
    }

    @Test
    fun `two consultations by one patient are two visits but one consulted patient`() {
        val stats = StatisticsUtils.visitTypeStats(listOf(
            visit(id = "v1", patientId = "pat", type = "CONSULTATION"),
            visit(id = "v2", patientId = "pat", type = "CONSULTATION")
        ))
        assertEquals(2, stats.totalConsultation)
        assertEquals(1, stats.consultedPatients)
        assertEquals(0, stats.totalVaccination)
    }

    @Test
    fun `vaccination and consultation count separately`() {
        val stats = StatisticsUtils.visitTypeStats(listOf(
            visit(id = "v1", patientId = "p1", type = "VACCINATION"),
            visit(id = "v2", patientId = "p2", type = "CONSULTATION")
        ))
        assertEquals(1, stats.totalVaccination)
        assertEquals(1, stats.totalConsultation)
        assertEquals(1, stats.consultedPatients)
    }

    @Test
    fun `unknown visit type is not counted`() {
        val stats = StatisticsUtils.visitTypeStats(listOf(visit(type = "OTHER")))
        assertEquals(VisitTypeStats(0, 0, 0), stats)
    }

    @Test
    fun `empty list yields zero counts`() {
        assertEquals(VisitTypeStats(0, 0, 0), StatisticsUtils.visitTypeStats(emptyList()))
    }

    @Test
    fun `visitTypeStatsForFilter counts ACTIVE consultation visits instead of filtering them out`() {
        // Regression for NCC-043: the old composition visitTypeStats(filterValidVaccinations(...))
        // dropped every consultation (consultations are persisted ACTIVE), leaving the
        // Overview cards and report series permanently at 0.
        val visits = listOf(
            visit(id = "v1", patientId = "p1", type = "CONSULTATION", status = ReminderStatus.ACTIVE),
            visit(id = "v2", patientId = "p2", type = "VACCINATION", status = ReminderStatus.COMPLETED),
            visit(id = "v3", patientId = "p3", type = "VACCINATION", status = ReminderStatus.ACTIVE) // pending
        )

        val stats = StatisticsUtils.visitTypeStatsForFilter(visits, "Overall")

        assertEquals(1, stats.totalConsultation)
        assertEquals(1, stats.consultedPatients)
        // visitTypeStats counts by visit_type, not status: the pending visit still counts here
        // (its doses are separately excluded from administered-dose metrics).
        assertEquals(2, stats.totalVaccination)

        // The pre-fix composition really did lose the consultation (proves the test bites).
        val broken = StatisticsUtils.visitTypeStats(StatisticsUtils.filterValidVaccinations(visits))
        assertEquals(0, broken.totalConsultation)
    }

    @Test
    fun `visitTypeStatsForFilter applies the date window before counting`() {
        val visits = listOf(
            visit(id = "v1", type = "CONSULTATION").copy(dateGiven = "2025-05-10"), // FY 25-26 Q1 (April-June)
            visit(id = "v2", type = "CONSULTATION").copy(dateGiven = "2026-05-10")  // FY 26-27
        )

        assertEquals(1, StatisticsUtils.visitTypeStatsForFilter(visits, "FY 25-26").totalConsultation)
        assertEquals(1, StatisticsUtils.visitTypeStatsForFilter(visits, "FY 25-26", fyQuarter = 1).totalConsultation)
        assertEquals(0, StatisticsUtils.visitTypeStatsForFilter(visits, "FY 25-26", fyQuarter = 2).totalConsultation)
        assertEquals(2, StatisticsUtils.visitTypeStatsForFilter(visits, "Overall").totalConsultation)
    }
}
