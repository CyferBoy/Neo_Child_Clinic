package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.WasteRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Indices added (large-data scalability pass): vaccineId (WasteDao.getWasteForVaccine),
// dateWasted (default sort order on every waste query), and isSynced (sync scan) - this
// table previously had no indices at all.
@Serializable
@Entity(
    tableName = "waste_records",
    indices = [Index("vaccineId"), Index("dateWasted"), Index("isSynced")]
)
data class WasteEntity(
    @PrimaryKey val id: String,
    @SerialName("vaccine_id") val vaccineId: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("brand_name") val brandName: String,
    @SerialName("batch_number") val batchNumber: String,
    @SerialName("expiry_date") val expiryDate: String,
    @SerialName("date_wasted") val dateWasted: String,
    val reason: String,
    val quantity: Int,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("is_synced") val isSynced: Boolean = false,
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
