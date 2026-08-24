package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.WasteRecord
import kotlinx.serialization.SerialName
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
    val updatedAt: String = "",
    val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
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
    updatedAt = updatedAt,
    isSynced = isSynced,
    createdBy = createdBy,
    updatedBy = updatedBy
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
    updatedAt = if (updatedAt.isEmpty()) com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp() else updatedAt,
    isSynced = this.isSynced || isSynced,
    createdBy = createdBy,
    updatedBy = updatedBy
)
