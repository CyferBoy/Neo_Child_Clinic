package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Vaccination(
    val id: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val patientClinicId: String = "",
    val dateGiven: String = "",
    val cashAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val totalPaid: Double = 0.0,
    val notes: String = "",
    val performedBy: String = "",
    val items: List<VaccinationItem> = emptyList(),
    val followUps: List<FollowUpRequirement> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
    val inventoryStatus: String = "PENDING",
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val receiptNumber: String = "",
    val withFees: Boolean = false,
    val doctorsAcc: Boolean = false,
    val vaccineIds: List<String> = emptyList() // Legacy support for validator
) {
    // Computed properties for legacy support
    val vaccineNames: List<String> get() = items.map { it.vaccineName }
    val batchNumbers: List<String> get() = items.map { it.batchNumber }
    val batchIds: List<String> get() = items.map { it.batchId }
    val expiryDates: List<String> get() = items.map { it.expiryDate }
    val nxtVaccineNames: List<String> get() = followUps.map { it.nextVaccineName }
    val nextDueDate: String get() = followUps.firstOrNull()?.dueDate ?: ""
}

@Serializable
data class VaccinationItem(
    val id: String = "",
    val vaccinationId: String = "",
    val vaccineId: String = "",
    val vaccineName: String = "",
    val batchId: String = "",
    val batchNumber: String = "",
    val expiryDate: String = "",
    val quantity: Int = 1,
    val mrp: Double = 0.0,
    val netRate: Double = 0.0
)

@Serializable
data class FollowUpRequirement(
    val nextVaccineId: String = "",
    val nextVaccineName: String = "",
    val dueDate: String = "",
    val basedOnVaccineId: String = ""
)
