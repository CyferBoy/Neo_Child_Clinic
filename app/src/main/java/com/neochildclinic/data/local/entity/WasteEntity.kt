package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.WasteRecord

import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "waste_records")
data class WasteEntity(
    @PrimaryKey val id: String,
    val vaccineId: String,
    val batchId: String,
    val brandName: String,
    val batchNumber: String,
    val expiryDate: String,
    val dateWasted: String,
    val reason: String,
    val quantity: Int,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false
)

fun WasteEntity.toDomain() = WasteRecord(
    id = id,
    vaccineId = vaccineId,
    batchId = batchId,
    brandName = brandName,
    batchNumber = batchNumber,
    expiryDate = expiryDate,
    dateWasted = dateWasted,
    reason = reason,
    quantity = quantity,
    updatedAt = updatedAt
)

fun WasteRecord.toEntity(isSynced: Boolean = false) = WasteEntity(
    id = id,
    vaccineId = vaccineId,
    batchId = batchId,
    brandName = brandName,
    batchNumber = batchNumber,
    expiryDate = expiryDate,
    dateWasted = dateWasted,
    reason = reason,
    quantity = quantity,
    updatedAt = updatedAt,
    isSynced = isSynced
)
