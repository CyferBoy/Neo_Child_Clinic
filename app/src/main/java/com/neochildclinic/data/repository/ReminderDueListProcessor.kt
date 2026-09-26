package com.neochildclinic.data.repository

import com.neochildclinic.core.utils.DateCategory
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.data.local.entity.toVaccination
import com.neochildclinic.domain.model.NextVaccinationSummary
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import java.util.Date
import java.util.UUID

data class ReminderStats(
    val dueToday: Int = 0,
    val dueTomorrow: Int = 0,
    val overdue: Int = 0,
    val completedToday: Int = 0,
    val dismissedToday: Int = 0,
    val notificationsSentToday: Int = 0
)

/**
 * Pure combination of visit + reminder entities into the processed Due/terminal
 * vaccination list and dashboard stats. Extracted from ReminderRepositoryImpl so the
 * date/status rules live apart from the repository's persistence + sync concerns.
 */
object ReminderDueListProcessor {

    fun process(
        vaccEntities: List<VisitEntity>,
        reminderEntities: List<ReminderEntity>
    ): List<Vaccination> {
        val allVaccinations = vaccEntities.map { it.toVaccination() }
        val result = mutableListOf<Vaccination>()

        // The reminders table is the source of truth for the Due section.
        val activeReminders = reminderEntities.filter { it.status == "ACTIVE" && it.reminderEnabled }
        val groupedActive = activeReminders.groupBy { it.patientId to it.dueDate }

        groupedActive.forEach { (key, group) ->
            val (patientId, dueDate) = key
            val firstInGroup = group.first()
            val status = ReminderStatus.ACTIVE

            // Link to original visit if available, otherwise create a shell
            val baseVaccination = allVaccinations.find { it.id == firstInGroup.originalVisitId } ?: Vaccination(
                id = UUID.randomUUID().toString(),
                patientId = patientId,
                visitType = "VACCINATION",
                status = status
            )

            result.add(baseVaccination.copy(
                nextVaccinations = group.map {
                    NextVaccinationSummary(
                        reminderId = it.id,
                        type = it.type,
                        vaccineNames = it.vaccineName.split(",").map(String::trim).filter(String::isNotBlank),
                        dueDate = it.dueDate
                    )
                },
                status = status,
                performedBy = firstInGroup.performedBy ?: ""
            ))
        }

        // Completed and dismissed records remain in reminders and are grouped for display.
        val terminalReminders = reminderEntities.filter {
            (it.status == "COMPLETED" && !it.reminderEnabled) ||
                (it.status == "DISMISSED" && it.reminderEnabled)
        }
        val groupedTerminal = terminalReminders.groupBy { Triple(it.patientId, it.dueDate, it.status) }

        groupedTerminal.forEach { (key, group) ->
            val (patientId, dueDate, statusStr) = key
            val status = try { ReminderStatus.valueOf(statusStr) } catch (_: Exception) { ReminderStatus.ACTIVE }

            val firstState = group.first()
            val vaccination = allVaccinations.find { it.id == firstState.originalVisitId }
            if (vaccination != null) {
                result.add(vaccination.copy(
                    nextVaccinations = group.map {
                        NextVaccinationSummary(
                            reminderId = it.id,
                            type = it.type,
                            vaccineNames = it.vaccineName.split(",").map(String::trim).filter(String::isNotBlank),
                            dueDate = it.dueDate
                        )
                    },
                    status = status,
                    dateGiven = when (status) {
                        ReminderStatus.COMPLETED -> PatientUtils.formatDateTime(Date(com.neochildclinic.core.utils.PatientUtils.isoToLong(firstState.completionDate)))
                        ReminderStatus.DISMISSED -> PatientUtils.formatDateTime(Date(com.neochildclinic.core.utils.PatientUtils.isoToLong(firstState.dismissalDate)))
                        else -> ""
                    },
                    performedBy = firstState.performedBy ?: "",
                    notes = group.mapNotNull { it.notes ?: it.dismissalReason }.distinct().joinToString(", ")
                ))
            }
        }

        return result
    }

    fun stats(dueList: List<Vaccination>, reminders: List<ReminderEntity>): ReminderStats {
        val todayCal = DateClassifier.getTodayStart()
        val todayStart = todayCal.timeInMillis

        return ReminderStats(
            dueToday = dueList.count {
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Today
            },
            dueTomorrow = dueList.count { DateClassifier.classify(it.nextDueDate, todayCal) is DateCategory.Tomorrow },
            overdue = dueList.count {
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Overdue
            },
            completedToday = reminders.count { it.status == "COMPLETED" && com.neochildclinic.core.utils.PatientUtils.isoToLong(it.completionDate) >= todayStart },
            dismissedToday = reminders.count { it.status == "DISMISSED" && com.neochildclinic.core.utils.PatientUtils.isoToLong(it.dismissalDate) >= todayStart },
            notificationsSentToday = reminders.count { it.notificationSent && it.lastReminderTime >= todayStart }
        )
    }
}