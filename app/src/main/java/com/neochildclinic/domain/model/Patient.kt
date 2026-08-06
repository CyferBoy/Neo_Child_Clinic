package com.neochildclinic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Patient(
    val id: String = "",
    @SerialName("patient_clinic_id") val patientClinicId: String? = null,
    val name: String = "",
    val phone: String = "",
    @SerialName("alternate_phone") val alternatePhone: String? = null,
    val dob: String = "",
    val gender: String = "",
    val address: String? = null,
    @SerialName("registration_date") val registrationDate: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val attachments: String? = null
)
