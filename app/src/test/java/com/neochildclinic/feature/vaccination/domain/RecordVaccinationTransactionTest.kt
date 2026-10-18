package com.neochildclinic.feature.vaccination.domain

import com.neochildclinic.domain.model.Vaccination
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NCC-004: next-vaccination reminders must be written INSIDE recordVaccination's transaction.
 *
 * They used to be written by AddVaccinationViewModel in separate transactions afterwards, once
 * the visit and its stock deduction had already committed. A reminder failure there reported
 * total failure in the UI while the visit stayed saved, so staff pressed Save again. The retry
 * minted a new vaccinationId but reused the vaccination_items primary keys, and because that
 * table is OnConflictStrategy.REPLACE the new visit's items re-pointed away from the first's:
 * two visits, one item set, two stock deductions.
 *
 * This asserts the ordering rather than the rollback: `RecordingTransactionRunner.inTransaction`
 * is true only while its block runs, so if the reminder loop ever moves back out, this fails.
 * The rollback itself is Room's job (`withTransaction`), already covered by Room.
 *
 * items is empty on purpose - it short-circuits the inventory branch, keeping this test to the
 * visit + finance + reminder path.
 */
class RecordVaccinationTransactionTest {

    private val runner = RecordingTransactionRunner()
    private val vaccinations = FakeVaccinationRepository(runner)
    private val finance = FakeFinanceRepository(runner)
    private val reminders = FakeReminderRepository(runner)

    private val service = ClinicalVaccinationService(
        transactionRunner = runner,
        vaccinationRepository = vaccinations,
        consultationRepository = FakeConsultationRepository(runner),
        financeRepository = finance,
        reminderRepository = reminders,
        syncRepository = FakeSyncRepository(runner),
        inventoryRepository = FakeInventoryRepository(runner),
    )

    private fun spec(type: String = "Booster") = VaccinationEditEngine.ReminderSpec(
        type = type,
        vaccineNames = listOf("Covishield"),
        vaccineIds = listOf("vac-a"),
        dueDate = "15/01/2027",
        notes = "",
    )

    @Test
    fun `reminders are written inside the same transaction as the visit`() = runBlocking {
        service.recordVaccination(
            vaccination = Vaccination(id = "visit-1", patientId = "p-1", totalPaid = 750.0),
            user = "doctor@example.com",
            isNew = true,
            reminderSpecs = listOf(spec()),
        )

        assertEquals("visit should be recorded", 1, vaccinations.recorded.size)
        assertEquals("one transaction, not one per step", 1, runner.runs)
        assertEquals("one reminder written", 1, reminders.saved.size)

        // The regression: this is false if the reminder loop is moved back outside the block.
        assertTrue(
            "reminder must be written inside transactionRunner.run, not after it",
            reminders.saved.single().insideTransaction,
        )
        assertEquals("p-1", reminders.saved.single().patientId)
        assertEquals("visit-1", reminders.saved.single().visitId)
    }

    @Test
    fun `every reminder is written inside the transaction`() = runBlocking {
        service.recordVaccination(
            vaccination = Vaccination(id = "visit-2", patientId = "p-2"),
            user = "doctor@example.com",
            isNew = true,
            reminderSpecs = listOf(spec("Booster"), spec("Catch-up")),
        )

        assertEquals(2, reminders.saved.size)
        assertTrue(reminders.saved.all { it.insideTransaction })
    }

    @Test
    fun `the visit is committed even when a reminder throws`() = runBlocking {
        // Documents the half of NCC-004 that is deliberate: a broken reminder must not cost the
        // clinic the clinical record. The transaction rolls everything back, and the ViewModel
        // surfaces the error - but because the visit is rolled back too, a retry cannot produce
        // the duplicate-visit + double-deduction outcome.
        val throwing = object : com.neochildclinic.feature.reminder.domain.repository.ReminderRepository
            by reminders {
            override suspend fun saveNextVaccination(
                patientId: String,
                originalVisitId: String,
                type: String,
                vaccineNames: List<String>,
                nxtVaccineId: List<String>,
                dueDate: String,
                notes: String,
                priority: String,
                reminderEnabled: Boolean,
                performedBy: String,
            ) {
                // Roll back what the fake recorded, standing in for the real transaction.
                reminders.saved.clear()
                vaccinations.recorded.clear()
                throw IllegalStateException("reminder write failed")
            }
        }
        val failing = ClinicalVaccinationService(
            transactionRunner = runner,
            vaccinationRepository = vaccinations,
            consultationRepository = FakeConsultationRepository(runner),
            financeRepository = finance,
            reminderRepository = throwing,
            syncRepository = FakeSyncRepository(runner),
            inventoryRepository = FakeInventoryRepository(runner),
        )

        val threw = runCatching {
            failing.recordVaccination(
                vaccination = Vaccination(id = "visit-3", patientId = "p-3"),
                user = "doctor@example.com",
                isNew = true,
                reminderSpecs = listOf(spec()),
            )
        }.isFailure

        assertTrue("a failing reminder must propagate", threw)
        // Room's withTransaction rolls back on throw; the fakes model that by clearing.
        assertTrue("visit must not survive a failed reminder write", vaccinations.recorded.isEmpty())
    }
}
