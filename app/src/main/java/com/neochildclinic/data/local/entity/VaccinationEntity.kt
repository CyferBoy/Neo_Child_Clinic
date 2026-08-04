package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.Vaccination
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
        Index("isDone")
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
    val patientId: String,
    val dateGiven: String,
    val doctor: String = "",
    val vaccineNames: String,
    val vaccineIds: String = "",
    val batchIds: String = "", // Comma separated list of batch UUIDs
    val batchNumbers: String = "", // Comma separated list of human-readable lot numbers
    val materialsUsed: String? = null,
    val notes: String = "",
    val receiptNumber: String = "",
    val totalPaid: Double = 0.0,
    val paymentId: String? = null, // Linked to finance_transactions
    
    // Legacy support / reminders logic preserved
    val nxtVaccineNames: String = "",
    val nextDueDate: String = "",
    val cost: Double = 0.0,
    val cashAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val withFees: Boolean = false,
    val doctorsAcc: Boolean = false,
    val isDone: Boolean = true,
    val source: String = "CLINIC",
    val inventoryStatus: String = "PENDING",
    
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false
)

// Map legacy VaccinationEntity name to VisitEntity for easier refactoring
typealias VaccinationEntity = VisitEntity

fun VisitEntity.toVaccination() = Vaccination(
    id = id,
    receiptNumber = receiptNumber,
    patientId = patientId,
    vaccineNames = if (vaccineNames.isBlank()) emptyList() else vaccineNames.split(","),
    vaccineIds = if (vaccineIds.isBlank()) emptyList() else vaccineIds.split(","),
    nxtVaccineNames = if (nxtVaccineNames.isBlank()) emptyList() else nxtVaccineNames.split(","),
    dateGiven = dateGiven,
    nextDueDate = nextDueDate,
    cost = cost,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    totalPaid = totalPaid,
    withFees = withFees,
    doctorsAcc = doctorsAcc,
    isDone = isDone,
    source = source,
    notes = notes,
    performedBy = doctor,
    batchNumbers = if (batchNumbers.isBlank()) emptyList() else batchNumbers.split(","),
    batchIds = if (batchIds.isBlank()) emptyList() else batchIds.split(","),
    expiryDates = emptyList(), // Not stored directly in visit anymore, link to batch
    updatedAt = updatedAt,
    inventoryStatus = inventoryStatus
)

fun Vaccination.toEntity(isSynced: Boolean = true) = VisitEntity(
    id = id,
    receiptNumber = receiptNumber,
    patientId = patientId,
    vaccineNames = vaccineNames.joinToString(","),
    vaccineIds = vaccineIds.joinToString(","),
    nxtVaccineNames = nxtVaccineNames.joinToString(","),
    dateGiven = dateGiven,
    nextDueDate = nextDueDate,
    cost = cost,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    totalPaid = totalPaid,
    withFees = withFees,
    doctorsAcc = doctorsAcc,
    isDone = isDone,
    source = source,
    notes = notes,
    doctor = performedBy,
    batchIds = this.batchIds.joinToString(","),
    batchNumbers = batchNumbers.joinToString(","),
    inventoryStatus = inventoryStatus,
    updatedAt = updatedAt,
    isSynced = isSynced
)
