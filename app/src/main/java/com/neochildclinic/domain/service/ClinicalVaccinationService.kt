package com.neochildclinic.domain.service

import androidx.room.withTransaction
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.SyncRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.core.model.SyncOperation
import com.neochildclinic.core.model.SyncPriority
import com.neochildclinic.core.utils.PatientUtils
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClinicalVaccinationService @Inject constructor(
    private val database: AppDatabase,
    private val vaccinationRepository: VaccinationRepository,
    private val consultationRepository: ConsultationRepository,
    private val financeRepository: FinanceRepository,
    private val reminderRepository: ReminderRepository,
    private val syncRepository: SyncRepository
) {
    suspend fun recordVaccination(
        vaccination: Vaccination,
        user: String,
        isNew: Boolean = true
    ) {
        val transactionGroupId = UUID.randomUUID().toString()
        database.withTransaction {
            // 1. Add/Update Vaccination Record
            vaccinationRepository.addVaccination(vaccination, transactionGroupId)

            // 2. Create the finance transaction only for a new vaccination.
            // Editing must update the existing transaction for this visit instead of
            // creating a second income record.
            if (isNew) {
                if (vaccination.totalPaid > 0) {
                    financeRepository.recordIncome(
                        amount = vaccination.totalPaid,
                        cashAmount = vaccination.cashAmount,
                        onlineAmount = vaccination.onlineAmount,
                        category = "VACCINATION",
                        patientId = vaccination.patientId,
                        visitId = vaccination.id,
                        remarks = "Vaccination: ${vaccination.items.joinToString(", ") { it.vaccineName }}",
                        recordedBy = user,
                        transactionGroupId = transactionGroupId
                    )
                }
            } else {
                financeRepository.updateIncomeForVisit(
                    visitId = vaccination.id,
                    amount = vaccination.totalPaid,
                    cashAmount = vaccination.cashAmount,
                    onlineAmount = vaccination.onlineAmount,
                    remarks = "Vaccination: ${vaccination.items.joinToString(", ") { it.vaccineName }}",
                    recordedBy = user,
                    transactionGroupId = transactionGroupId
                )
            }

            // 3. If this is a newly administered vaccination, complete any existing
            // reminder rows for this visit/vaccine directly. No legacy requirement object or
            // automatic reminder-engine calculation is involved.
            if (isNew) satisfyRelatedReminders(vaccination, user)
        }
    }

    suspend fun recordConsultation(
        consultation: Consultation,
        user: String
    ) {
        val transactionGroupId = UUID.randomUUID().toString()
        database.withTransaction {
            val visitId = if (consultation.visitId.isBlank()) UUID.randomUUID().toString() else consultation.visitId
            
            // 1. Create Visit Header
            val visit = VisitEntity(
                id = visitId,
                patientId = consultation.patientId,
                dateGiven = consultation.date,
                doctorId = consultation.doctorId,
                doctor = consultation.doctorName,
                notes = consultation.problem,
                visitType = "CONSULTATION",
                cashAmount = consultation.cashAmount,
                onlineAmount = consultation.onlineAmount,
                totalPaid = consultation.amount,
                updatedAt = com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp(),
                isSynced = false
            )
            database.vaccinationDao().insertVaccination(visit)
            
            // Sync visit header
            syncRepository.enqueue(
                entityName = "VISIT",
                entityId = visitId,
                operation = SyncOperation.CREATE,
                priority = SyncPriority.HIGH,
                transactionGroupId = transactionGroupId
            )
            
            // 2. Create Consultation Record
            val finalConsultation = consultation.copy(visitId = visitId)
            consultationRepository.addConsultation(finalConsultation, transactionGroupId)
            
            // 3. Record Financial Transaction
            financeRepository.recordIncome(
                amount = consultation.amount,
                cashAmount = consultation.cashAmount,
                onlineAmount = consultation.onlineAmount,
                category = "CONSULTATION",
                patientId = consultation.patientId,
                visitId = visitId,
                remarks = "Consultation: ${consultation.problem}",
                recordedBy = user,
                transactionGroupId = transactionGroupId
            )
        }
    }

    private suspend fun satisfyRelatedReminders(vaccination: Vaccination, user: String) {
        val reminders = reminderRepository.getRemindersByVisitId(vaccination.id)
        val givenNames = vaccination.items.map {
            PatientUtils.cleanVaccineName(it.vaccineName).lowercase().trim()
        }
        reminders.forEach { reminder ->
            val reminderNames = reminder.vaccineName.split(", ").map {
                PatientUtils.cleanVaccineName(it).lowercase().trim()
            }.filter { it.isNotBlank() }
            val matches = reminderNames.isEmpty() || reminderNames.any { it in givenNames }
            if (matches && reminder.status != "COMPLETED" && reminder.status != "DISMISSED") {
                reminderRepository.markReminderCompleted(reminder, user, vaccination.id)
            }
        }
    }

}
