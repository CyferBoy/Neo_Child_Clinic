package com.neochildclinic.core.sync

import com.neochildclinic.feature.sync.domain.runRefreshStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Audit finding 15: one failing repository refresh must not abort the rest of
// RefreshDataUseCase's pipeline. The step runner records the failure and the pipeline
// moves on; RefreshDataUseCase then throws one aggregated error naming every failed step.
class RefreshStepTest {

    @Test
    fun `a failing step is recorded and later steps still run`() = runBlocking {
        val executed = mutableListOf<String>()

        val failure = runRefreshStep("Patients") {
            executed += "Patients"
            throw IllegalStateException("boom")
        }
        val success = runRefreshStep("Vaccinations") {
            executed += "Vaccinations"
        }

        assertEquals("Patients: boom", failure)
        assertNull("A successful step must report no failure", success)
        assertEquals(
            "The pipeline must continue past a failed step",
            listOf("Patients", "Vaccinations"),
            executed
        )
    }
}
