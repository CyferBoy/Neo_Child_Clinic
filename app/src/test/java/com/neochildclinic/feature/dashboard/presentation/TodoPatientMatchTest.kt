package com.neochildclinic.feature.dashboard.presentation

import com.neochildclinic.domain.model.Patient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoPatientMatchTest {

    private fun patient(id: String, name: String, phone: String) = Patient(
        id = id, name = name, phone = phone, dob = "2020-01-01", gender = "M"
    )

    private val patients = listOf(
        patient("p1", "Asha Rao", "9876543210"),
        patient("p2", "Asha Rao", "9123456789"),
        patient("p3", "Ravi Kumar", "")
    )

    @Test
    fun `selected id wins`() {
        assertEquals("p2", resolveTodoPatientId("p2", "Asha Rao", "9876543210", patients))
    }

    @Test
    fun `falls back to exact name and phone`() {
        assertEquals("p1", resolveTodoPatientId(null, " asha rao ", "98-765 43210", patients))
    }

    @Test
    fun `same name different phone returns null`() {
        assertNull(resolveTodoPatientId(null, "Asha Rao", "9000000000", patients))
    }

    @Test
    fun `blank mobile never matches`() {
        assertNull(resolveTodoPatientId(null, "Ravi Kumar", "", patients))
        assertNull(resolveTodoPatientId(null, "Ravi Kumar", "   ", patients))
    }

    @Test
    fun `duplicate name and phone does not guess`() {
        val duplicates = listOf(
            patient("p1", "Asha Rao", "9876543210"),
            patient("p4", "Asha Rao", "9876543210")
        )
        assertNull(resolveTodoPatientId(null, "Asha Rao", "9876543210", duplicates))
    }
}
