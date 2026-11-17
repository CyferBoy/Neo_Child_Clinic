package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Visit(
    val id: String = "",
    val patientId: String = "",
    val dateGiven: String = "",
    val doctorId: String = "",
    val doctor: String = "",
    val visitType: String = "VACCINATION",
    val cashAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val totalPaid: Double = 0.0,
    val notes: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val createdBy: String? = null,
    val updatedBy: String? = null
)