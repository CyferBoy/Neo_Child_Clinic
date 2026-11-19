package com.neochildclinic.feature.sync.domain

import com.neochildclinic.core.sync.RefreshStep
import com.neochildclinic.core.sync.RefreshTables
import com.neochildclinic.core.sync.refreshTablesSteps
import com.neochildclinic.core.sync.stepsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Scoped refreshes (report item F1): realtime/patient-list triggers must pull only the
// tables they show - NOT the whole 13-step pipeline (full pulls stay for manual sync and
// pull-to-refresh). The mapping is the logic: each table's closure includes its FK
// dependencies so a scoped pull lands rows its Room foreign keys need.
class RefreshTablesTest {

    @Test
    fun `visits need patients pulled first`() {
        assertEquals(
            setOf(RefreshStep.PATIENTS, RefreshStep.VACCINATIONS),
            stepsFor(listOf(RefreshTables.VISITS))
        )
    }

    @Test
    fun `vaccination items depend on inventory landing first`() {
        val steps = stepsFor(listOf(RefreshTables.VACCINATION_ITEMS))!!
        assertEquals(setOf(RefreshStep.INVENTORY, RefreshStep.VACCINATION_ITEMS), steps)
    }

    @Test
    fun `personal reminders depend on inventory for their vaccine foreign key`() {
        val steps = stepsFor(listOf(RefreshTables.PERSONAL_REMINDERS))!!
        assertEquals(setOf(RefreshStep.INVENTORY, RefreshStep.PERSONAL_REMINDERS), steps)
    }

    @Test
    fun `patient list scope is the union of its tables without duplicate patients`() {
        val steps = stepsFor(listOf(RefreshTables.PATIENTS, RefreshTables.VISITS, RefreshTables.CONSULTATIONS))!!
        assertEquals(
            setOf(RefreshStep.PATIENTS, RefreshStep.VACCINATIONS, RefreshStep.CONSULTATIONS),
            steps
        )
    }

    @Test
    fun `doctor availability is in the todos scope`() {
        assertTrue("The TODOS scope must pull doctor availability for the slot picker",
            RefreshStep.DOCTOR_AVAILABILITY in stepsFor(listOf(RefreshTables.TODOS))!!)
    }

    @Test
    fun `no scope means the full pipeline untouched`() {
        assertNull("An empty scope must run every step, as before the scoping API existed",
            stepsFor(emptyList()))
    }

    @Test
    fun `every table entry has a mapping - a forgotten one must fail here, not at runtime`() {
        RefreshTables.entries.forEach { table ->
            assertTrue("RefreshTables.$table has no steps mapping", table in refreshTablesSteps)
        }
    }

    @Test
    fun `every pipeline step is mapped to a scope - a new or renamed step must fail here`() {
        val scopedSteps = refreshTablesSteps.values.flatten().toSet()
        RefreshStep.entries.forEach { step ->
            assertTrue("Pipeline step ${step.displayName} is in no scope map; scoped refresh would skip it",
                step in scopedSteps)
        }
    }
}
