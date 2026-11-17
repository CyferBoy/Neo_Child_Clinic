package com.neochildclinic.feature.reminder.data
import com.neochildclinic.core.common.DateUtils

import com.neochildclinic.core.common.DateCategory
import com.neochildclinic.core.common.DateClassifier
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.data.local.entity.toVaccination
import com.neochildclinic.domain.model.NextVaccinationSummary
import com.neochildclinic.domain.model.ReminderStats
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import java.util.Date
import java.util.UUID

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
            val baseVaccination = allVaccinations.find { it.id == firstState.originalVisitId } ?: Vaccination(
                id = UUID.randomUUID().toString(),
                patientId = patientId,
                visitType = "VACCINATION",
                status = status
            )

            val terminalDateFormatted = when (status) {
                ReminderStatus.COMPLETED -> {
                    val rawDate = firstState.completionDate
                    if (!rawDate.isNullOrBlank()) {
                        val ts = DateUtils.isoToLong(rawDate)
                        if (ts > 0L) DateUtils.formatDateTime(Date(ts)) else ""
                    } else ""
                }
                ReminderStatus.DISMISSED -> {
                    val rawDate = firstState.dismissalDate
                    if (!rawDate.isNullOrBlank()) {
                        val ts = DateUtils.isoToLong(rawDate)
                        if (ts > 0L) DateUtils.formatDateTime(Date(ts)) else ""
                    } else ""
                }
                else -> ""
            }

            result.add(baseVaccination.copy(
                originalDateGiven = baseVaccination.dateGiven,
                statusDate = terminalDateFormatted,
                nextVaccinations = group.map {
                    NextVaccinationSummary(
                        reminderId = it.id,
                        type = it.type,
                        vaccineNames = it.vaccineName.split(",").map(String::trim).filter(String::isNotBlank),
                        dueDate = it.dueDate
                    )
                },
                status = status,
                dateGiven = if (terminalDateFormatted.isNotBlank()) terminalDateFormatted else baseVaccination.dateGiven,
                performedBy = firstState.performedBy ?: "",
                notes = group.mapNotNull { it.notes ?: it.dismissalReason }.distinct().joinToString(", ")
            ))
        }

        return result
    }

    /** Due-today tile count on a processed due list: ACTIVE rows only, classified Today.
     *  Terminal rows (COMPLETED/DISMISSED) also carry dueDate strings and must never count,
     *  which is why this is not simply `stats(dueList).dueToday` (report item F4). */
    fun dueTodayCount(dueList: List<Vaccination>): Int {
        val todayCal = DateClassifier.getTodayStart()
        return dueList.filter { it.status == ReminderStatus.ACTIVE }
            .count { DateClassifier.classify(it.nextDueDate, todayCal) is DateCategory.Today }
    }

    fun stats(dueList: List<Vaccination>, reminders: List<ReminderEntity>): ReminderStats {
        val todayCal = DateClassifier.getTodayStart()
        val todayStart = todayCal.timeInMillis

        return ReminderStats(
            dueToday = dueList.filter { it.status == ReminderStatus.ACTIVE }.count {
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Today
            },
            dueTomorrow = dueList.filter { it.status == ReminderStatus.ACTIVE }.count { DateClassifier.classify(it.nextDueDate, todayCal) is DateCategory.Tomorrow },
            overdue = dueList.filter { it.status == ReminderStatus.ACTIVE }.count {
                val cat = DateClassifier.classify(it.nextDueDate, todayCal)
                cat is DateCategory.Overdue
            },
            completedToday = reminders.count {
                it.status == "COMPLETED" && !it.completionDate.isNullOrBlank() && com.neochildclinic.core.common.DateUtils.isoToLong(it.completionDate) >= todayStart
            },
            dismissedToday = reminders.count {
                it.status == "DISMISSED" && !it.dismissalDate.isNullOrBlank() && com.neochildclinic.core.common.DateUtils.isoToLong(it.dismissalDate) >= todayStart
            }
        )
    }
}