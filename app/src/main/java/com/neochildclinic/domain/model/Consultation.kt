package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Consultation(
    val id: String = "",
    val visitId: String = "",
    val patientId: String = "",
    val doctorId: String = "",
    val doctorName: String = "",
    val date: String = "", // yyyy-MM-dd
    val amount: Double = 0.0,
    val cashAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val problem: String = "",
    val notes: String = "",
    val nextFollowUpDate: String = "",
    val updatedAt: String = ""
)
