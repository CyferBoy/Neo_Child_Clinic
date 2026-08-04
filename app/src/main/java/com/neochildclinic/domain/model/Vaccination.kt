package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Vaccination(
    val id: String = "",
    val receiptNumber: String = "",
    val patientId: String = "",
    val vaccineNames: List<String> = emptyList(),
    val vaccineIds: List<String> = emptyList(),
    val nxtVaccineNames: List<String> = emptyList(),
    val dateGiven: String = "",
    val nextDueDate: String = "",
    val cost: Double = 0.0,
    val cashAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val totalPaid: Double = 0.0,
    val withFees: Boolean = false,
    val doctorsAcc: Boolean = false,
    val isDone: Boolean = false,
    val source: String = VaccinationSource.CLINIC.name,
    val notes: String = "",
    val rescheduleReason: String = "",
    val performedBy: String = "",
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val batchNumbers: List<String> = emptyList(),
    val batchIds: List<String> = emptyList(),
    val expiryDates: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val inventoryStatus: String = "PENDING"
)
