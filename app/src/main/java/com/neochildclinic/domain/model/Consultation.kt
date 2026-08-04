package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Consultation(
    val id: String = "",
    val patientId: String = "",
    val date: String = "", // yyyy-MM-dd
    val amount: Double = 0.0,
    val notes: String = "",
    val nextFollowUpDate: String = ""
)
