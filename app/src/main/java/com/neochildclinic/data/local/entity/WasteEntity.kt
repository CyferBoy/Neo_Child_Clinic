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
    indices = [Index("vaccineId"), Index("dateWasted"), Index("isSynced"), Index("batchId")]
)
data class WasteEntity(
    @PrimaryKey @SerialName("id") val id: String,
    @SerialName("vaccine_id") val vaccineId: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("brand_name") val brandName: String,
    @SerialName("batch_number") val batchNumber: String,
    @SerialName("expiry_date") val expiryDate: String,
    @SerialName("date_wasted") val dateWasted: String,
    @SerialName("reason") val reason: String,
    @SerialName("quantity") val quantity: Int,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("is_synced") override val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
) : SyncableEntity

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
    updatedAt = if (updatedAt.isEmpty()) com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp() else updatedAt,
    isSynced = isSynced,
    createdBy = createdBy,
    updatedBy = updatedBy
)
