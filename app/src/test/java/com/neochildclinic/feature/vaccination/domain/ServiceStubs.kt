package com.neochildclinic.feature.vaccination.domain

// GENERATED test doubles for ClinicalVaccinationService's collaborators.
// Methods are TODO() unless the recordVaccination happy path calls them, so an unexpected
// dependency surfaces as NotImplementedError naming the method instead of a silent no-op.
// Declarations are copied from the interfaces, so they cannot drift. Regenerate, do not hand-edit.

import com.neochildclinic.core.database.TransactionRunner
import com.neochildclinic.domain.model.*
import kotlinx.coroutines.flow.*
import com.neochildclinic.feature.vaccination.domain.repository.VaccinationRepository
import com.neochildclinic.feature.consultation.domain.repository.ConsultationRepository
import com.neochildclinic.feature.finance.domain.repository.FinanceRepository
import com.neochildclinic.feature.reminder.domain.repository.ReminderRepository
import com.neochildclinic.feature.sync.domain.repository.SyncRepository
import com.neochildclinic.feature.inventory.domain.repository.InventoryRepository

data class ReminderWrite(val insideTransaction: Boolean, val patientId: String, val visitId: String, val type: String)

/** Set true while [TransactionRunner.run] is executing, false outside it. */
class RecordingTransactionRunner : TransactionRunner {
    var inTransaction = false
        private set
    var runs = 0
        private set

    override suspend fun <T> run(block: suspend () -> T): T {
        runs++
        inTransaction = true
        try {
            return block()
        } finally {
            inTransaction = false
        }
    }
}

class FakeVaccinationRepository(private val runner: RecordingTransactionRunner) : VaccinationRepository {
    val recorded = mutableListOf<Vaccination>()
    override val allVaccinations: Flow<List<Vaccination>> get() = TODO()
    override fun getVaccinationsForPatient(patientId: String): Flow<List<Vaccination>> = TODO()
    override suspend fun addVaccination(vaccination: Vaccination, transactionGroupId: String? ): Boolean { recorded += vaccination; return true }
    override suspend fun refreshVaccinations() = TODO()
    override suspend fun fetchRemoteVaccinationItems(): List<VaccinationItem> = TODO()
    override suspend fun applyDownloadedVaccinationItems(items: List<VaccinationItem>) = TODO()
    override suspend fun transferVaccinations(duplicateId: String, masterId: String) = TODO()
    override suspend fun getVaccinationById(id: String): Vaccination? = TODO()
    override suspend fun deleteVaccination(id: String) = TODO()
    override fun getVaccinationCardsForPatient(patientId: String): Flow<List<PatientVaccinationCard>> = TODO()
    override suspend fun insertVisit(visit: Visit) = TODO()
    override suspend fun updateVisitInventoryStatus(vaccinationId: String, status: String) = TODO()
}

class FakeConsultationRepository(private val runner: RecordingTransactionRunner) : ConsultationRepository {
    override suspend fun addConsultation(consultation: Consultation, transactionGroupId: String? ) = TODO()
    override suspend fun updateConsultation(consultation: Consultation, transactionGroupId: String? ) = TODO()
    override suspend fun deleteConsultation(id: String) = TODO()
    override suspend fun getConsultationById(id: String): Consultation? = TODO()
    override fun getConsultationsForPatient(patientId: String): Flow<List<Consultation>> = TODO()
    override suspend fun refreshConsultations() = TODO()
}

class FakeFinanceRepository(private val runner: RecordingTransactionRunner) : FinanceRepository {
    var recordedIncome = 0
    override fun getAllTransactions(): Flow<List<FinanceTransaction>> = TODO()
    override suspend fun recordIncome( amount: Double, cashAmount: Double, onlineAmount: Double, category: String, patientId: String?, visitId: String?, remarks: String?, recordedBy: String, transactionGroupId: String? ) { recordedIncome += 1 }
    override suspend fun updateConsultationIncome( visitId: String, consultationId: String, originalAmount: Double, originalCashAmount: Double, originalOnlineAmount: Double, amount: Double, cashAmount: Double, onlineAmount: Double, remarks: String?, recordedBy: String, transactionGroupId: String? ) = TODO()
    override suspend fun updateIncomeForVisit( visitId: String, amount: Double, cashAmount: Double, onlineAmount: Double, remarks: String?, recordedBy: String, transactionGroupId: String? ) = TODO()
    override suspend fun migrateLegacyVaccinationCogs(vaccinations: List<Vaccination>) = TODO()
    override suspend fun refreshTransactions() = TODO()
}

class FakeReminderRepository(private val runner: RecordingTransactionRunner) : ReminderRepository {
    val saved = mutableListOf<ReminderWrite>()
    override fun getDueList( searchQuery: String , filterStatus: List<ReminderStatus>? ): Flow<List<Vaccination>> = TODO()
    override fun getPatientReminders(patientId: String): Flow<List<Reminder>> = TODO()
    override fun getAuditTrail(patientId: String): Flow<List<ReminderAudit>> = TODO()
    override suspend fun getRemindersByVisitId(visitId: String): List<Reminder> { return emptyList() }
    override suspend fun markReminderCompleted( reminder: Reminder, performedBy: String, linkedVaccinationId: String? , transactionGroupId: String? ) = TODO()
    override suspend fun deleteReminder(reminder: Reminder, performedBy: String) = TODO()
    override suspend fun saveNextVaccination( patientId: String, originalVisitId: String, type: String, vaccineNames: List<String>, nxtVaccineId: List<String> , dueDate: String, notes: String, priority: String , reminderEnabled: Boolean , performedBy: String ) { saved += ReminderWrite(runner.inTransaction, patientId, originalVisitId, type) }
    override suspend fun transferReminders(duplicateId: String, masterId: String) = TODO()
    override suspend fun refreshReminders() = TODO()
    override fun getDashboardStats(): Flow<ReminderStats> = TODO()
    override suspend fun getReminderById(id: String): Reminder? = TODO()
    override fun getAllReminders(): Flow<List<Reminder>> = TODO()
    override fun getUpcomingVaccinations(): Flow<List<Reminder>> = TODO()
    override fun getUpcomingVaccinationsByType(type: String): Flow<List<Reminder>> = TODO()
    override fun getUpcomingVaccinationsByVaccineId(vaccineId: String): Flow<List<Reminder>> = TODO()
    override suspend fun reschedule( reminder: Reminder, newDate: String, reminderDate: String, reason: String, performedBy: String ) = TODO()
    override suspend fun dismissReminder(reminder: Reminder, reason: String, performedBy: String) = TODO()
    override suspend fun restoreReminder(reminder: Reminder, performedBy: String) = TODO()
    override suspend fun cancelNextVaccinationVaccine( reminder: Reminder, vaccineId: String, reason: String, performedBy: String ) = TODO()
}

class FakeSyncRepository(private val runner: RecordingTransactionRunner) : SyncRepository {
    override suspend fun enqueue( entityName: String, entityId: String, operation: SyncOperation, priority: SyncPriority , transactionGroupId: String? ) = TODO()
    override val syncState: StateFlow<SyncState> get() = TODO()
    override fun getPendingCount(): kotlinx.coroutines.flow.Flow<Int> = TODO()
    override suspend fun processNextItems(): Boolean = TODO()
}

class FakeInventoryRepository(private val runner: RecordingTransactionRunner) : InventoryRepository {
    override fun getInventoryItems( query: String , filter: InventoryFilter , sort: InventorySort ): Flow<List<InventoryItem>> = TODO()
    override fun getAllVaccines(): Flow<List<Vaccine>> = TODO()
    override fun getVaccineBatches(vaccineId: String): Flow<List<VaccineBatch>> = TODO()
    override suspend fun getBatchById(batchId: String): VaccineBatch? = TODO()
    override suspend fun getVaccineById(vaccineId: String): Vaccine? = TODO()
    override suspend fun addVaccine(vaccine: Vaccine, user: String) = TODO()
    override suspend fun updateVaccine(vaccine: Vaccine, user: String) = TODO()
    override suspend fun addBatch(batch: VaccineBatch, user: String, transactionGroupId: String? ) = TODO()
    override suspend fun addStockBatch(entriesByVaccine: Map<String, List<VaccineBatch>>, user: String) = TODO()
    override suspend fun updateBatch(batch: VaccineBatch, user: String, notes: String? ) = TODO()
    override suspend fun deleteBatch(batchId: String, user: String) = TODO()
    override suspend fun deleteVaccine(vaccineId: String, user: String) = TODO()
    override suspend fun getStockHistoryPage( vaccineId: String? , batchId: String? , types: List<InventoryTransactionType> , fromDateIso: String? , toDateIso: String? , limit: Int , offset: Int , remoteOnly: Boolean ): List<InventoryTransaction> = TODO()
    override suspend fun deductStock( vaccineId: String, quantity: Int, user: String, transactionType: InventoryTransactionType, visitId: String? , patientId: String? ) = TODO()
    override suspend fun addStockToBatch( batchId: String, quantity: Int, user: String, transactionType: InventoryTransactionType, notes: String? ) = TODO()
    override suspend fun returnBorrowedStock( originalBatchId: String, returnToBatchId: String, quantity: Int, user: String, notes: String? , transactionGroupId: String? ) = TODO()
    override suspend fun deductStockFromBatch( batchId: String, quantity: Int, user: String, transactionType: InventoryTransactionType, visitId: String? , patientId: String? , notes: String? , allowExpired: Boolean , givenDate: String? , transactionGroupId: String? ) = TODO()
    override suspend fun reverseDeduction( batchId: String, quantity: Int, user: String, visitId: String? , patientId: String? , transactionGroupId: String? ) = TODO()
    override suspend fun transferPatientTransactions(duplicateId: String, masterId: String) = TODO()
    override suspend fun getInventoryDeductionsForVaccination(vaccinationId: String): List<InventoryDeduction> = TODO()
    override suspend fun insertInventoryDeduction(entity: InventoryDeduction) = TODO()
    override suspend fun deleteInventoryDeductionsForVaccination(vaccinationId: String) = TODO()
    override suspend fun refreshInventory() = TODO()
}
