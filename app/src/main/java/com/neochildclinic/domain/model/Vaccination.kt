package com.neochildclinic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The domain model needs only a comparable instant to pick the earliest due date.
// shortcut: these mirror DateUtils.parseDate's accepted forms, kept here so domain
// does not depend on core.common; upgrade to a shared domain parser if more fields need it.
private val DUE_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
private val DUE_DATE_FORMATTERS = listOf(
    "d MMM yyyy", "d/M/yyyy", "dd/MM/yyyy", "yyyy-MM-dd"
).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
private val DUE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

private fun dueDateMillis(value: String): Long {
    if (value.isBlank()) return Long.MAX_VALUE
    runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()?.let { return it }
    runCatching { OffsetDateTime.parse(value.replace(' ', 'T')).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
    runCatching { LocalDateTime.parse(value, DUE_DATE_TIME_FORMATTER).atZone(DUE_ZONE).toInstant().toEpochMilli() }
        .getOrNull()?.let { return it }
    for (formatter in DUE_DATE_FORMATTERS) {
        runCatching { LocalDate.parse(value, formatter).atStartOfDay(DUE_ZONE).toInstant().toEpochMilli() }
            .getOrNull()?.let { return it }
    }
    return Long.MAX_VALUE
}

@Serializable
data class VaccinationItem(
    val id: String = "",
    val vaccinationId: String = "",
    val vaccineId: String = "",
    val vaccineName: String = "",
    val batchId: String = "",
    val batchNumber: String = "",
    val quantity: Int = 1,
    val mrp: Double = 0.0,
    val netRate: Double = 0.0,
    val expiryDate: String = ""
)

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
    @SerialName("availability_slot_id") val availabilitySlotId: String? = null,
    @SerialName("raw_vaccine_names") val rawVaccineNames: String = "", // Fallback for legacy records
    @SerialName("vaccine_ids") val vaccineIds: List<String> = emptyList(), // Legacy support for validator
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("original_date_given") val originalDateGiven: String? = null,
    @SerialName("status_date") val statusDate: String? = null
) {
    // Computed properties for legacy records: fall back to the visit-level snapshot when
    // no persisted item carries a usable vaccine name.
    val vaccineNames: List<String> get() = items.map { it.vaccineName }.filter { it.isNotBlank() }.ifEmpty { 
        if (rawVaccineNames.isNotBlank()) {
            rawVaccineNames.split(",").map { it.trim() }.filter { it.isNotEmpty() } 
        } else emptyList()
    }
    val batchNumbers: List<String> get() = items.map { it.batchNumber }
    val batchIds: List<String> get() = items.map { it.batchId }
    val expiryDates: List<String> get() = items.map { it.expiryDate }
    val nxtVaccineNames: List<String> get() = nextVaccinations.flatMap { it.vaccineNames }
    // Compare parsed dates, not strings: the stored format ("d MMM yyyy") is not zero-padded,
    // so "16 Oct 2026" sorts BEFORE "9 Oct 2026" as text while being a month later as a date.
    val nextDueDate: String
        get() = nextVaccinations
            .minByOrNull { dueDateMillis(it.dueDate) }
            ?.dueDate
            ?: ""
}

@Serializable
data class NextVaccinationSummary(
    val reminderId: String = "",
    val type: String = "",
    val vaccineNames: List<String> = emptyList(),
    val dueDate: String = ""
)
