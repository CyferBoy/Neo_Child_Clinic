package com.neochildclinic.domain.service

import androidx.room.withTransaction
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.repository.ConsultationRepository
import com.neochildclinic.domain.repository.FinanceRepository
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.domain.model.PendingRequirement
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
        isNew: Boolean = true,
        requirement: PendingRequirement? = null
    ) {
        val transactionGroupId = UUID.randomUUID().toString()
        database.withTransaction {
            // 1. Add/Update Vaccination Record
            vaccinationRepository.addVaccination(vaccination, transactionGroupId)

            // 2. Record Financial Transaction (if amount > 0)
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

            // 3. Reminder Engine Satisfaction
            if (requirement != null) {
                reminderRepository.markRequirementSatisfied(requirement, user, vaccination.id)
            } else if (isNew) {
                satisfyRelatedReminders(vaccination, user)
            }
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
        val existingReminders = reminderRepository.getPatientFollowUps(vaccination.patientId).first()
        val activeReminders = existingReminders.filter { 
            (it.status == "ACTIVE" || it.status == "RESCHEDULED") 
        }

        val givenCleaned = vaccination.items.map { 
            PatientUtils.cleanVaccineName(it.vaccineName).lowercase().trim() 
        }

        for (reminder in activeReminders) {
            // Split grouped reminders and check if any match what was given today
            val reminderVaccines = reminder.vaccineName.split(", ")
            for (rv in reminderVaccines) {
                val rvCleaned = PatientUtils.cleanVaccineName(rv).lowercase().trim()
                if (givenCleaned.contains(rvCleaned)) {
                    reminderRepository.markRequirementSatisfied(
                        PendingRequirement(
                            patientId = reminder.patientId,
                            vaccineName = rv, // Use the specific vaccine name from the group
                            dueDate = PatientUtils.parseDate(reminder.dueDate) ?: java.util.Date(),
                            originalVisitId = reminder.originalVisitId
                        ),
                        user,
                        vaccination.id
                    )
                }
            }
        }
    }
}
