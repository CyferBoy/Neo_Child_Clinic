package com.neochildclinic.domain.logic

import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.PendingRequirement
import java.util.*

/**
 * Pure Requirement Calculator.
 * Identifies potential gaps in a patient's vaccination schedule based purely
 * on medical records. It does NOT check manual overrides (dismissed, rescheduled).
 */
object ReminderEngine {

    /**
     * Analyzes vaccination history to find requirements that haven't been medically satisfied.
     * A requirement is satisfied if ANY visit occurring ON OR AFTER the
     * visit that created the requirement contains this vaccine in its "Gave" list.
     */
    fun getPotentialRequirements(allVaccinations: List<Vaccination>): List<PendingRequirement> {
        val patientVisits = allVaccinations.groupBy { it.patientId }
        return patientVisits.flatMap { (_, visits) -> 
            getPotentialRequirementsForPatient(visits) 
        }
    }

    /**
     * Analyzes vaccination history for a SINGLE patient.
     */
    fun getPotentialRequirementsForPatient(visits: List<Vaccination>): List<PendingRequirement> {
        if (visits.isEmpty()) return emptyList()
        
        val requirements = mutableListOf<PendingRequirement>()
        val sortedVisits = visits.sortedBy { PatientUtils.parseDate(it.dateGiven) ?: Date(0) }
        val patientId = sortedVisits.first().patientId

        // Pre-calculate cleaned names of given vaccines to avoid repeated string operations
        val givenVaccinesByVisit = sortedVisits.map { visit ->
            visit.items.map { PatientUtils.cleanVaccineName(it.vaccineName).lowercase().trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }

        for (i in sortedVisits.indices) {
            val visit = sortedVisits[i]
            if (visit.nextDueDate.isBlank() || visit.nxtVaccineNames.isEmpty()) continue

            val dueDate = PatientUtils.parseDate(visit.nextDueDate) ?: continue

            for (dueVaccineName in visit.nxtVaccineNames) {
                val cleanedDueName = PatientUtils.cleanVaccineName(dueVaccineName).lowercase().trim()
                if (cleanedDueName.isBlank()) continue

                // Check if satisfied by this or any later visit
                var isSatisfied = false
                for (j in i until sortedVisits.size) {
                    if (givenVaccinesByVisit[j].contains(cleanedDueName)) {
                        isSatisfied = true
                        break
                    }
                }

                if (!isSatisfied) {
                    requirements.add(
                        PendingRequirement(
                            patientId = patientId,
                            vaccineName = dueVaccineName,
                            dueDate = dueDate,
                            originalVisitId = visit.id
                        )
                    )
                }
            }
        }
        return requirements
    }
}
