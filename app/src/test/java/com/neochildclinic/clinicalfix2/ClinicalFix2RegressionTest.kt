package com.neochildclinic.clinicalfix2

import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.InventoryDeduction
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.VaccinationItem
import com.neochildclinic.domain.repository.InventoryRepository
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.feature.inventory.data.deducted
import com.neochildclinic.feature.vaccination.domain.ClinicalVaccinationService
import com.neochildclinic.feature.vaccination.domain.FakeFinanceRepository
import com.neochildclinic.feature.vaccination.domain.FakeInventoryRepository
import com.neochildclinic.feature.vaccination.domain.FakeReminderRepository
import com.neochildclinic.feature.vaccination.domain.FakeSyncRepository
import com.neochildclinic.feature.vaccination.domain.FakeVaccinationRepository
import com.neochildclinic.feature.vaccination.domain.RecordingTransactionRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** WST returned-waste routing and REM future-due reminder matching. */
class ClinicalFix2RegressionTest {

    @Test
    fun `returned waste added then deleted keeps the wasted count`() {
        val original = VaccineBatchEntity(
            batchId = "b1",
            vaccineId = "v1",
            batchNumber = "B1",
            manufacturer = "M",
            purchaseDate = "2025-01-01",
            expiryDate = "2030-01-01",
            purchaseQuantity = 10,
            remainingQuantity = 5,
            supplier = "S",
            purchaseCost = 10.0,
            sellingPrice = 20.0,
            wastedQuantity = 3
        )

        // waste 3, then a waste record with reason "returned" (-> RETURN) for 2.
        val afterReturn = original.deducted(2, InventoryTransactionType.RETURN, "nurse")
        assertEquals(3, afterReturn.remainingQuantity)
        assertEquals(5, afterReturn.wastedQuantity)

        // deleteWaste reverses via addStockToBatch(MANUAL_ADJUSTMENT): +remaining, -wasted.
        val afterDelete = afterReturn.copy(
            remainingQuantity = afterReturn.remainingQuantity + 2,
            wastedQuantity = (afterReturn.wastedQuantity - 2).coerceAtLeast(0)
        )
        assertEquals(5, afterDelete.remainingQuantity)
        assertEquals(3, afterDelete.wastedQuantity)
    }

    private class CapturingReminderRepo(
        delegate: ReminderRepository,
        private val reminders: List<Reminder>
    ) : ReminderRepository by delegate {
        val completed = mutableListOf<Reminder>()

        override fun getPatientReminders(patientId: String): Flow<List<Reminder>> = flowOf(reminders)
        override suspend fun markReminderCompleted(
            reminder: Reminder,
            performedBy: String,
            linkedVaccinationId: String?,
            transactionGroupId: String?
        ) {
            completed += reminder
        }
    }

    @Test
    fun `a future due reminder stays open while a past due one completes`() = runBlocking {
        val runner = RecordingTransactionRunner()
        val past = reminder("past", "01 Jan 2026")
        val future = reminder("future", "15 Jan 2028")
        val unparseable = reminder("bad", "not-a-date")
        val reminders = CapturingReminderRepo(
            FakeReminderRepository(runner),
            listOf(past, future, unparseable)
        )
        val service = ClinicalVaccinationService(
            transactionRunner = runner,
            vaccinationRepository = object : VaccinationRepository by FakeVaccinationRepository(runner) {
                override suspend fun updateVisitInventoryStatus(vaccinationId: String, status: String) = Unit
            },
            financeRepository = FakeFinanceRepository(runner),
            reminderRepository = reminders,
            syncRepository = object : SyncRepository by FakeSyncRepository(runner) {
                override suspend fun enqueue(
                    entityName: String, entityId: String, operation: SyncOperation,
                    priority: SyncPriority, transactionGroupId: String?
                ) = Unit
            },
            inventoryRepository = object : InventoryRepository by FakeInventoryRepository(runner) {
                override suspend fun deductStockFromBatch(
                    batchId: String, quantity: Int, user: String,
                    transactionType: InventoryTransactionType, visitId: String?, patientId: String?,
                    notes: String?, givenDate: String?, transactionGroupId: String?
                ) = Unit
                override suspend fun insertInventoryDeduction(entity: InventoryDeduction) = Unit
            }
        )

        service.recordVaccination(
            vaccination = Vaccination(
                id = "visit-1",
                patientId = "p1",
                dateGiven = "18 Nov 2026",
                items = listOf(VaccinationItem(vaccineName = "DPT", vaccineId = "v-dpt"))
            ),
            user = "doctor@example.com",
            isNew = true
        )

        assertEquals(listOf("past"), reminders.completed.map { it.id })
    }

    private fun reminder(id: String, dueDate: String) = Reminder(
        id = id,
        patientId = "p1",
        originalVisitId = "old-visit",
        vaccineName = "DPT",
        dueDate = dueDate,
        status = "ACTIVE"
    )
}
