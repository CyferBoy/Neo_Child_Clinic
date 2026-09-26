package com.neochildclinic.features.statistics

import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.statistics.StatisticsUtils
import com.neochildclinic.domain.statistics.VisitTypeStats
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
}