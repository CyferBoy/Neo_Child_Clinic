package com.neochildclinic.feature.dashboard.presentation

import com.neochildclinic.domain.model.ConsultationTodo
import com.neochildclinic.domain.model.VaccinationTodo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateConsultationGateTest {

    private fun consult(
        visitType: String = "NEW",
        patientId: String? = "p1"
    ) = ConsultationTodo(
        id = "c1", patientId = patientId, name = "Asha Rao", mobile = "9876543210",
        address = "", todoDate = "2026-11-19", visitType = visitType
    )

    @Test
    fun `new waiting linked todo is allowed`() {
        assertTrue(canCreateConsultationFromTodo(consult(), isCompleted = false, hasCallback = true))
    }

    @Test
    fun `legacy blank visit type is treated as new`() {
        assertTrue(canCreateConsultationFromTodo(consult(visitType = ""), isCompleted = false, hasCallback = true))
    }

    @Test
    fun `follow-up todo is not allowed`() {
        assertFalse(canCreateConsultationFromTodo(consult(visitType = "FOLLOW_UP"), isCompleted = false, hasCallback = true))
    }

    @Test
    fun `unknown visit type is not allowed`() {
        assertFalse(canCreateConsultationFromTodo(consult(visitType = "FOLLOWUP"), isCompleted = false, hasCallback = true))
    }

    @Test
    fun `completed todo is not allowed`() {
        assertFalse(canCreateConsultationFromTodo(consult(), isCompleted = true, hasCallback = true))
    }

    @Test
    fun `unlinked todo is not allowed`() {
        assertFalse(canCreateConsultationFromTodo(consult(patientId = null), isCompleted = false, hasCallback = true))
        assertFalse(canCreateConsultationFromTodo(consult(patientId = "  "), isCompleted = false, hasCallback = true))
    }

    @Test
    fun `vaccination todo is not allowed`() {
        val item = VaccinationTodo(
            id = "v1", patientId = "p1", name = "Asha Rao", mobile = "9876543210",
            vaccineNames = "MMR", address = "", todoDate = "2026-11-19"
        )
        assertFalse(canCreateConsultationFromTodo(item, isCompleted = false, hasCallback = true))
    }

    @Test
    fun `no callback is not allowed`() {
        assertFalse(canCreateConsultationFromTodo(consult(), isCompleted = false, hasCallback = false))
    }

    @Test
    fun `unlinked consultation shows add patient`() {
        assertTrue(shouldShowAddPatient(consult(patientId = null)))
        assertTrue(shouldShowAddPatient(consult(patientId = "  ")))
    }

    @Test
    fun `linked consultation hides add patient`() {
        assertFalse(shouldShowAddPatient(consult(patientId = "p1")))
    }

    @Test
    fun `unlinked vaccination shows add patient`() {
        val item = VaccinationTodo(
            id = "v1", name = "Asha Rao", mobile = "9876543210",
            vaccineNames = "MMR", address = "", todoDate = "2026-11-19"
        )
        assertTrue(shouldShowAddPatient(item))
    }

    @Test
    fun `unknown item hides add patient`() {
        assertFalse(shouldShowAddPatient(null))
    }
}
