package com.neochildclinic.domain.service

import androidx.room.withTransaction
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.domain.model.PendingRequirement
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.VaccinationRepository
import com.neochildclinic.core.utils.PatientUtils
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClinicalVaccinationService @Inject constructor(
    private val database: AppDatabase,
    private val vaccinationRepository: VaccinationRepository,
    private val reminderRepository: ReminderRepository
) {
    suspend fun recordVaccination(
        vaccination: Vaccination,
        user: String,
        isNew: Boolean = true,
        requirement: PendingRequirement? = null
    ) {
        database.withTransaction {
            // 1. Add/Update Vaccination Record
            vaccinationRepository.addVaccination(vaccination)

            // 2. Reminder Engine Satisfaction
            if (requirement != null) {
                reminderRepository.markRequirementSatisfied(requirement, user, vaccination.id)
            } else if (isNew) {
                satisfyRelatedReminders(vaccination, user)
            }
        }
    }

    private suspend fun satisfyRelatedReminders(vaccination: Vaccination, user: String) {
        val existingReminders = reminderRepository.getPatientFollowUps(vaccination.patientId).first()
        val activeReminders = existingReminders.filter { 
            (it.status == "ACTIVE" || it.status == "RESCHEDULED") && !it.isDeleted 
        }

        val givenCleaned = vaccination.vaccineNames.map { 
            PatientUtils.cleanVaccineName(it).lowercase().trim() 
        }

        for (reminder in activeReminders) {
            val reminderCleaned = PatientUtils.cleanVaccineName(reminder.vaccineName).lowercase().trim()
            if (givenCleaned.contains(reminderCleaned)) {
                reminderRepository.markRequirementSatisfied(
                    PendingRequirement(
                        patientId = reminder.patientId,
                        vaccineName = reminder.vaccineName,
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
