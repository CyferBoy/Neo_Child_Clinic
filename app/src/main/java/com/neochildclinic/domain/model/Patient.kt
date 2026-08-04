package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Patient(
    val id: String = "",
    val patientClinicId: String = "", // e.g., NEO-001
    val name: String = "",
    val phone: String = "",
    val alternatePhone: String = "",
    val dob: String = "",
    val gender: String = "",
    val address: String = "",
    val registrationDate: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
