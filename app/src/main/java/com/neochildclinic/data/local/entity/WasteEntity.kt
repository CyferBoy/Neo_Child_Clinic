package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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
    @PrimaryKey val id: String = "",
    @SerialName("vaccine_id") val vaccineId: String = "",
    @SerialName("batch_id") val batchId: String = "",
    @SerialName("brand_name") val brandName: String = "",
    @SerialName("batch_number") val batchNumber: String = "",
    @SerialName("expiry_date") val expiryDate: String = "",
    @SerialName("date_wasted") val dateWasted: String = "",
    val reason: String = "",
    val quantity: Int = 1,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("is_synced") val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null,
    @SerialName("is_deleted") @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @SerialName("deleted_by") @ColumnInfo(name = "deleted_by") val deletedBy: String? = null
)
