package com.neochildclinic.core.sync

import com.neochildclinic.feature.sync.domain.RefreshTables
import com.neochildclinic.feature.sync.domain.refreshTablesSteps
import com.neochildclinic.feature.sync.domain.stepsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Scoped refreshes (report item F1): realtime/patient-list triggers must pull only the
// tables they show - NOT the whole 13-step pipeline (full pulls stay for manual sync and
// pull-to-refresh). The mapping is the logic: each table's closure includes its FK
// dependencies so a scoped pull lands rows its Room foreign keys need.
class RefreshTablesTest {

    @Test
    fun `visits need patients pulled first`() {
        assertEquals(
            setOf("Patients", "Vaccinations"),
            stepsFor(listOf(RefreshTables.VISITS))
        )
    }

    @Test
    fun `vaccination items depend on inventory landing first`() {
        val steps = stepsFor(listOf(RefreshTables.VACCINATION_ITEMS))!!
        assertEquals(setOf("Inventory", "Vaccination items"), steps)
    }

    @Test
    fun `personal reminders depend on inventory for their vaccine foreign key`() {
        val steps = stepsFor(listOf(RefreshTables.PERSONAL_REMINDERS))!!
        assertEquals(setOf("Inventory", "Personal reminders"), steps)
    }

    @Test
    fun `patient list scope is the union of its tables without duplicate patients`() {
        val steps = stepsFor(listOf(RefreshTables.PATIENTS, RefreshTables.VISITS, RefreshTables.CONSULTATIONS))!!
        assertEquals(setOf("Patients", "Vaccinations", "Consultations"), steps)
    }

    @Test
    fun `no scope means the full pipeline untouched`() {
        assertNull("An empty scope must run every step, as before the scoping API existed",
            stepsFor(emptyList()))
    }

    @Test
    fun `every table entry has a mapping - a forgotten one must fail here, not at runtime`() {
        RefreshTables.entries.forEach { table ->
            org.junit.Assert.assertTrue(
                "RefreshTables.$table has no steps mapping", table in refreshTablesSteps
            )
        }
    }
}
