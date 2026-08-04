package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.*
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
    val patientId: String,
    val dateGiven: String,
    val doctor: String = "",
    val vaccineNames: String = "",
    val vaccineIds: String = "",
    val batchIds: String = "", // Comma separated list of batch UUIDs
    val batchNumbers: String = "", // Comma separated list of human-readable lot numbers
    val materialsUsed: String? = null,
    val notes: String = "",
    val receiptNumber: String = "",
    val totalPaid: Double = 0.0,
    val paymentId: String? = null, // Linked to finance_transactions
    
    // Reminders logic preserved
    val nxtVaccineNames: String = "",
    val nextDueDate: String = "",
    val cashAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val withFees: Boolean = false,
    val doctorsAcc: Boolean = false,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val source: String = "CLINIC",
    val inventoryStatus: String = "PENDING",
    
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true
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
    performedBy = doctor,
    updatedAt = updatedAt,
    inventoryStatus = inventoryStatus,
    status = status,
    receiptNumber = receiptNumber,
    withFees = withFees,
    doctorsAcc = doctorsAcc
)

fun Vaccination.toEntity(isSynced: Boolean = true) = VisitEntity(
    id = id,
    patientId = patientId,
    dateGiven = dateGiven,
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
    inventoryStatus = inventoryStatus,
    updatedAt = updatedAt,
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
