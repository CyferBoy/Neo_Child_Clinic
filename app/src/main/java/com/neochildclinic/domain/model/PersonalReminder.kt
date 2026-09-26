package com.neochildclinic.domain.model

data class PersonalReminder(
    val id: String,
    val patientId: String? = null,
    val patientName: String,
    val patientPhone: String,
    val vaccineId: String? = null,
    val vaccineLabel: String? = null,
    val note: String? = null,
    val advanceReceived: Boolean = false,
    val advanceAmount: Double? = null,
    val advanceDate: String? = null,
    val reminderDate: String? = null,
    val status: String = "PENDING",
    val createdAt: String = "",
    val updatedAt: String = "",
    val completedAt: String? = null,
    val cancelledAt: String? = null
)