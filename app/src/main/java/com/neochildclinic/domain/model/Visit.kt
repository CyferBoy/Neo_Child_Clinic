package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Visit(
    val id: String = "",
    val patientId: String = "",
    val dateGiven: String = "",
    val doctorId: String = "",
    val doctor: String = "",
    val visitType: String = "VACCINATION",
    val cashAmount: Double = 0.0,
    val onlineAmount: Double = 0.0,
    val totalPaid: Double = 0.0,
    val notes: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val createdBy: String? = null,
    val updatedBy: String? = null,
    val isSynced: Boolean = false
)

fun Visit.toEntity() = com.neochildclinic.data.local.entity.VisitEntity(
    id = id,
    patientId = patientId,
    dateGiven = dateGiven,
    doctorId = doctorId,
    doctor = doctor,
    visitType = visitType,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    totalPaid = totalPaid,
    notes = notes,
    receiptNumber = "",
    vaccineNames = "",
    vaccineIds = "",
    batchIds = "",
    batchNumbers = "",
    materialsUsed = null,
    withFees = false,
    doctorsAcc = false,
    status = ReminderStatus.ACTIVE,
    source = "CLINIC",
    inventoryStatus = "PENDING",
    availabilitySlotId = null,
    createdAt = createdAt.ifBlank { com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp() },
    updatedAt = updatedAt.ifBlank { com.neochildclinic.core.common.PatientUtils.getCurrentIsoTimestamp() },
    isSynced = isSynced,
    createdBy = createdBy,
    updatedBy = updatedBy,
)