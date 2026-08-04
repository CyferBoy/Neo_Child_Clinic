package com.neochildclinic.domain.model

import java.util.Date

/**
 * Represents a medically unsatisfied requirement.
 */
data class PendingRequirement(
    val patientId: String,
    val vaccineName: String,
    val dueDate: Date,
    val originalVisitId: String
)
