package com.neochildclinic.domain.model

/**
 * Pure Kotlin business domain model for a patient.
 * Decoupled from SQLite/Room annotations and persistence sync state (UNTANGLE-09).
 */
data class Patient(
    val id: String,
    val patientClinicId: String? = null,
    val uhid: String = "",
    val name: String,
    val phone: String,
    val alternatePhone: String? = null,
    val dob: String,
    val gender: String,
    val address: String? = null,
    val registrationDate: String? = null,
    val attachments: String? = null,
    val updatedAt: String? = null,
    val createdBy: String? = null,
    val updatedBy: String? = null
)
