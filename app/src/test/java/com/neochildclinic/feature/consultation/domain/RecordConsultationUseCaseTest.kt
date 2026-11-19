package com.neochildclinic.feature.consultation.domain

import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.SyncOperation
import com.neochildclinic.domain.model.SyncPriority
import com.neochildclinic.domain.model.SyncState
import com.neochildclinic.domain.model.Visit
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.feature.consultation.domain.usecase.RecordConsultationUseCase
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordConsultationUseCaseTest {

    private class ImmediateTransactionRunner : TransactionRunner {
        var ranInTransaction = false
        override suspend fun <T> run(block: suspend () -> T): T {
            ranInTransaction = true
            return block()
        }
    }

    private class TestVaccinationRepository : VaccinationRepository {
        val insertedVisits = mutableListOf<Visit>()
        override suspend fun insertVisit(visit: Visit) {
            insertedVisits.add(visit)
        }
        override val allVaccinations: Flow<List<com.neochildclinic.domain.model.Vaccination>> = emptyFlow()
        override suspend fun addVaccination(vaccination: com.neochildclinic.domain.model.Vaccination, transactionGroupId: String?): Boolean = TODO()
        override suspend fun refreshVaccinations() = TODO()
        override suspend fun fetchRemoteVaccinationItems(): List<com.neochildclinic.domain.model.VaccinationItem> = TODO()
        override suspend fun applyDownloadedVaccinationItems(items: List<com.neochildclinic.domain.model.VaccinationItem>) = TODO()
        override suspend fun transferVaccinations(duplicateId: String, masterId: String) = TODO()
        override suspend fun getVaccinationById(id: String): com.neochildclinic.domain.model.Vaccination? = TODO()
        override suspend fun deleteVaccination(id: String, transactionGroupId: String?) = TODO()
        override fun getVaccinationsForPatient(patientId: String): Flow<List<com.neochildclinic.domain.model.Vaccination>> = emptyFlow()
        override fun getVaccinationCardsForPatient(patientId: String): Flow<List<com.neochildclinic.domain.model.PatientVaccinationCard>> = emptyFlow()
        override suspend fun updateVisitInventoryStatus(vaccinationId: String, status: String) = TODO()
    }

    private class TestConsultationRepository : ConsultationRepository {
        var savedConsultation: Consultation? = null
        var passedGroupId: String? = null
        override suspend fun addConsultation(consultation: Consultation, transactionGroupId: String?) {
            savedConsultation = consultation
            passedGroupId = transactionGroupId
        }
        override suspend fun updateConsultation(consultation: Consultation, transactionGroupId: String?) = TODO()
        override suspend fun deleteConsultation(id: String) = TODO()
        override suspend fun getConsultationById(id: String): Consultation? = TODO()
        override fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>> = emptyFlow()
        override suspend fun refreshConsultations() = TODO()
    }

    private class TestFinanceRepository : FinanceRepository {
        var recordedIncomeArgs: IncomeRecordArgs? = null
        data class IncomeRecordArgs(
            val amount: Double,
            val cashAmount: Double,
            val onlineAmount: Double,
            val category: String,
            val patientId: String?,
            val visitId: String?,
            val remarks: String?,
            val recordedBy: String,
            val transactionGroupId: String?
        )
        override suspend fun recordIncome(
            amount: Double,
            cashAmount: Double,
            onlineAmount: Double,
            category: String,
            patientId: String?,
            visitId: String?,
            remarks: String?,
            recordedBy: String,
            transactionGroupId: String?
        ) {
            recordedIncomeArgs = IncomeRecordArgs(
                amount, cashAmount, onlineAmount, category, patientId, visitId, remarks, recordedBy, transactionGroupId
            )
        }
        override suspend fun updateConsultationIncome(
            visitId: String,
            consultationId: String,
            originalAmount: Double,
            originalCashAmount: Double,
            originalOnlineAmount: Double,
            amount: Double,
            cashAmount: Double,
            onlineAmount: Double,
            remarks: String?,
            recordedBy: String,
            transactionGroupId: String?
        ) = TODO()
        override suspend fun updateIncomeForVisit(
            visitId: String,
            amount: Double,
            cashAmount: Double,
            onlineAmount: Double,
            remarks: String?,
            recordedBy: String,
            transactionGroupId: String?
        ) = TODO()
        override suspend fun migrateLegacyVaccinationCogs(vaccinations: List<com.neochildclinic.domain.model.Vaccination>) = TODO()
        override fun getAllTransactions(): Flow<List<FinanceTransaction>> = emptyFlow()
        override suspend fun refreshTransactions() = TODO()
    }

    private class TestSyncRepository : SyncRepository {
        val enqueued = mutableListOf<Enqueued>()
        data class Enqueued(
            val entityName: String,
            val entityId: String,
            val operation: SyncOperation,
            val priority: SyncPriority,
            val transactionGroupId: String?
        )
        override suspend fun enqueue(
            entityName: String,
            entityId: String,
            operation: SyncOperation,
            priority: SyncPriority,
            transactionGroupId: String?
        ) {
            enqueued.add(Enqueued(entityName, entityId, operation, priority, transactionGroupId))
        }
        override val syncState: StateFlow<SyncState> = MutableStateFlow(SyncState.IDLE)
        override fun getPendingCount(): Flow<Int> = emptyFlow()
        override suspend fun processNextItems(): Boolean = TODO()
        override suspend fun processNextItemsWithOwnership(): com.neochildclinic.domain.repository.SyncRunResult = TODO()
        override suspend fun hasFailedItems(): Boolean = TODO()
        override suspend fun getUnacknowledgedCount(): Int = TODO()
        override suspend fun clearLocalData() = TODO()
        override fun getSyncQueue(): kotlinx.coroutines.flow.Flow<List<com.neochildclinic.domain.model.SyncItem>> = TODO()
        override suspend fun clearSyncedItems() = TODO()
        override suspend fun retryItem(queueId: Long) = TODO()
        override suspend fun deleteQueueItem(queueId: Long) = TODO()
        override suspend fun retryFailedItems() = TODO()
        override suspend fun deleteAllFailed() = TODO()
    }

    @Test
    fun `invoke coordinates visit, consultation, sync enqueue and income with shared transactionGroupId`() = runBlocking {
        val runner = ImmediateTransactionRunner()
        val vaccinationRepo = TestVaccinationRepository()
        val consultationRepo = TestConsultationRepository()
        val financeRepo = TestFinanceRepository()
        val syncRepo = TestSyncRepository()

        val useCase = RecordConsultationUseCase(
            transactionRunner = runner,
            vaccinationRepository = vaccinationRepo,
            consultationRepository = consultationRepo,
            financeRepository = financeRepo,
            syncRepository = syncRepo
        )

        val consultation = Consultation(
            id = "c-123",
            patientId = "pat-456",
            doctorId = "doc-1",
            doctorName = "Dr. Smith",
            date = "2026-11-17",
            amount = 500.0,
            cashAmount = 300.0,
            onlineAmount = 200.0,
            problem = "Fever and cough"
        )

        useCase(consultation, "nurse@clinic.com")

        assertTrue("Must execute inside transaction", runner.ranInTransaction)
        assertEquals("Visit header must be inserted", 1, vaccinationRepo.insertedVisits.size)
        val visit = vaccinationRepo.insertedVisits.first()
        assertEquals("pat-456", visit.patientId)
        assertEquals("CONSULTATION", visit.visitType)
        assertEquals(500.0, visit.totalPaid, 0.001)

        val enqueuedVisit = syncRepo.enqueued.firstOrNull { it.entityName == "VISIT" }
        assertNotNull("VISIT sync must be enqueued", enqueuedVisit)
        val groupId = enqueuedVisit!!.transactionGroupId
        assertNotNull("transactionGroupId must not be null", groupId)

        assertEquals("Consultation must be passed shared groupId", groupId, consultationRepo.passedGroupId)
        assertEquals("Consultation must be associated with visitId", visit.id, consultationRepo.savedConsultation?.visitId)

        val incomeArgs = financeRepo.recordedIncomeArgs
        assertNotNull("Income must be recorded", incomeArgs)
        assertEquals("Income must be passed shared groupId", groupId, incomeArgs!!.transactionGroupId)
        assertEquals(500.0, incomeArgs.amount, 0.001)
        assertEquals(visit.id, incomeArgs.visitId)
    }
}
