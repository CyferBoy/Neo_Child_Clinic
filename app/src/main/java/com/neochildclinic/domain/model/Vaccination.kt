package com.neochildclinic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Vaccination(
    val id: String = "",
    @SerialName("patient_id") val patientId: String = "",
    @SerialName("patient_name") val patientName: String = "",
    @SerialName("patient_clinic_id") val patientClinicId: String? = null,
    @SerialName("date_given") val dateGiven: String = "",
    @SerialName("cash_amount") val cashAmount: Double = 0.0,
    @SerialName("online_amount") val onlineAmount: Double = 0.0,
    @SerialName("total_paid") val totalPaid: Double = 0.0,
    val notes: String = "",
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("performed_by") val performedBy: String = "",
    val items: List<VaccinationItem> = emptyList(),
    val nextVaccinations: List<NextVaccinationSummary> = emptyList(),
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("inventory_status") val inventoryStatus: String = "PENDING",
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    @SerialName("visit_type") val visitType: String = "VACCINATION",
    val source: String = "CLINIC",
    @SerialName("receipt_number") val receiptNumber: String = "",
    @SerialName("with_fees") val withFees: Boolean = false,
    @SerialName("doctors_acc") val doctorsAcc: Boolean = false,
    @SerialName("raw_vaccine_names") val rawVaccineNames: String = "", // Fallback for legacy records
    @SerialName("vaccine_ids") val vaccineIds: List<String> = emptyList() // Legacy support for validator
) {
    // Computed properties for legacy support
    val vaccineNames: List<String> get() = items.map { it.vaccineName }.ifEmpty { 
        if (rawVaccineNames.isNotBlank()) {
            rawVaccineNames.split(",").map { it.trim() }.filter { it.isNotEmpty() } 
        } else emptyList()
    }
    val batchNumbers: List<String> get() = items.map { it.batchNumber }
    val batchIds: List<String> get() = items.map { it.batchId }
    val expiryDates: List<String> get() = items.map { it.expiryDate }
    val nxtVaccineNames: List<String> get() = nextVaccinations.flatMap { it.vaccineNames }
    val nextDueDate: String get() = nextVaccinations.minByOrNull { it.dueDate }?.dueDate ?: ""
}

@Serializable
data class VaccinationItem(
    val id: String = "",
    @SerialName("vaccination_id") val vaccinationId: String = "",
    @SerialName("vaccine_id") val vaccineId: String = "",
    @SerialName("vaccine_name") val vaccineName: String = "",
    @SerialName("batch_id") val batchId: String = "",
    @SerialName("batch_number") val batchNumber: String = "",
    @SerialName("expiry_date") val expiryDate: String = "",
    val quantity: Int = 1,
    val mrp: Double = 0.0,
    @SerialName("net_rate") val netRate: Double = 0.0
)

@Serializable
data class NextVaccinationSummary(
    val reminderId: String = "",
    val type: String = "",
    val vaccineNames: List<String> = emptyList(),
    val dueDate: String = ""
)
