package com.neochildclinic.data.local.mapper

import com.neochildclinic.core.common.DateUtils
import com.neochildclinic.data.local.entity.VisitEntity
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Visit

fun Visit.toEntity(isSynced: Boolean = false): VisitEntity = VisitEntity(
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
    createdAt = createdAt.ifBlank { DateUtils.getCurrentIsoTimestamp() },
    updatedAt = updatedAt.ifBlank { DateUtils.getCurrentIsoTimestamp() },
    isSynced = isSynced,
    createdBy = createdBy,
    updatedBy = updatedBy
)

fun VisitEntity.toDomain(): Visit = Visit(
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
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
    createdBy = createdBy,
    updatedBy = updatedBy
)
