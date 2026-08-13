package com.neochildclinic.domain.logic

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
    /**
     * Legacy: History-based requirement calculation is disabled.
     * The `reminders` table is now the sole source of truth for explicitly scheduled doses.
     */
    fun getPotentialRequirements(@Suppress("UNUSED_PARAMETER") allVaccinations: List<Vaccination>): List<PendingRequirement> {
        return emptyList()
    }

    /**
     * Legacy: History-based requirement calculation is disabled.
     */
    fun getPotentialRequirementsForPatient(@Suppress("UNUSED_PARAMETER") visits: List<Vaccination>): List<PendingRequirement> {
        return emptyList()
    }
}
