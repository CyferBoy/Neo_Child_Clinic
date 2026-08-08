package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a Clinic Visit for vaccination.
 * Organized under Patient as primary business entity.
 */
@Serializable
@Entity(
    tableName = "patient_visits",
    indices = [
        Index("patientId"), 
        Index("receiptNumber"),
        Index("doctor"),
        Index("isSynced"),
        Index("status")
    ],
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VisitEntity(
    @PrimaryKey val id: String,
    @SerialName("patient_id") val patientId: String,
    @SerialName("date_given") val dateGiven: String,
    @SerialName("doctor_id") val doctorId: String = "",
    val doctor: String = "",
    @SerialName("vaccine_names") val vaccineNames: String = "",
    @SerialName("vaccine_ids") val vaccineIds: String = "",
    @SerialName("batch_ids") val batchIds: String = "", // Comma separated list of batch UUIDs
    @SerialName("batch_numbers") val batchNumbers: String = "", // Comma separated list of human-readable lot numbers
    @SerialName("materials_used") val materialsUsed: String? = null,
    val notes: String = "",
    @SerialName("receipt_number") val receiptNumber: String = "",
    @SerialName("total_paid") val totalPaid: Double = 0.0,
    @SerialName("payment_id") val paymentId: String? = null, // Linked to finance_transactions
    
    // Reminders logic preserved
    @SerialName("nxt_vaccine_names") val nxtVaccineNames: String = "",
    @SerialName("next_due_date") val nextDueDate: String = "",
    @SerialName("cash_amount") val cashAmount: Double = 0.0,
    @SerialName("online_amount") val onlineAmount: Double = 0.0,
    @SerialName("with_fees") val withFees: Boolean = false,
    @SerialName("doctors_acc") val doctorsAcc: Boolean = false,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val source: String = "CLINIC",
    @SerialName("visit_type") val visitType: String = "VACCINATION",
    @SerialName("inventory_status") val inventoryStatus: String = "PENDING",
    
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = true
)

// Map legacy VaccinationEntity name to VisitEntity for easier refactoring
typealias VaccinationEntity = VisitEntity

fun VisitEntity.toVaccination() = Vaccination(
    id = id,
    patientId = patientId,
    dateGiven = dateGiven,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    totalPaid = totalPaid,
    notes = notes,
    doctorId = doctorId,
    performedBy = doctor,
    updatedAt = updatedAt ?: "",
    inventoryStatus = inventoryStatus,
    status = status,
    visitType = visitType,
    rawVaccineNames = vaccineNames,
    receiptNumber = receiptNumber,
    withFees = withFees,
    doctorsAcc = doctorsAcc
)

fun Vaccination.toEntity(isSynced: Boolean = true) = VisitEntity(
    id = id,
    patientId = patientId,
    dateGiven = dateGiven,
    doctorId = doctorId,
    doctor = performedBy,
    vaccineNames = items.joinToString(",") { it.vaccineName },
    vaccineIds = items.joinToString(",") { it.vaccineId },
    batchIds = items.joinToString(",") { it.batchId },
    batchNumbers = items.joinToString(",") { it.batchNumber },
    notes = notes,
    receiptNumber = receiptNumber,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    totalPaid = totalPaid,
    withFees = withFees,
    doctorsAcc = doctorsAcc,
    status = status,
    visitType = "VACCINATION",
    inventoryStatus = inventoryStatus,
    createdAt = if (updatedAt.isEmpty()) null else updatedAt,
    updatedAt = if (updatedAt.isEmpty()) null else updatedAt,
    isSynced = isSynced,
    nxtVaccineNames = followUps.joinToString(",") { it.nextVaccineName },
    nextDueDate = followUps.firstOrNull()?.dueDate ?: ""
)

fun VaccinationItem.toEntity() = VaccinationItemEntity(
    id = if (id.isBlank()) java.util.UUID.randomUUID().toString() else id,
    vaccinationId = vaccinationId,
    vaccineId = vaccineId,
    batchId = batchId,
    quantity = quantity,
    mrp = mrp,
    netRate = netRate,
    expiryDate = expiryDate
)
