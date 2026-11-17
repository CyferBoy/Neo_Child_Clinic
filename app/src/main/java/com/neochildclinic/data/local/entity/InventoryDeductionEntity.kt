package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.neochildclinic.domain.model.InventoryDeduction

// @Serializable added for Backup & Restore (see data/backup/BackupModels.kt) so this
// table can be exported/imported the same way every other entity already is. Purely
// additive - does not change the Room schema, column names, or any existing behavior.
//
// Indices added (large-data scalability pass): vaccinationId and status are the only
// columns InventoryDeductionDao filters on (getForVaccination / getCompletedForVaccination),
// and this table previously had no indices at all, forcing a full table scan per lookup.
@Serializable
@Entity(
    tableName = "inventory_deductions",
    indices = [
        Index("vaccinationId"),
        Index("status"),
        Index("batchId"),
        Index("vaccineId")
    ]
)
data class InventoryDeductionEntity(
    @PrimaryKey(autoGenerate = true) @SerialName("id") val id: Long = 0,
    @SerialName("vaccination_id") val vaccinationId: String,
    @SerialName("vaccine_id") val vaccineId: String,
    @SerialName("vaccine_name") val vaccineName: String,
    @SerialName("batch_id") val batchId: String?,      // null until resolved/attempted
    @SerialName("quantity") val quantity: Int,
    @SerialName("status") val status: String,        // "COMPLETED" or "FAILED"
    @SerialName("error_message") val errorMessage: String?,
    @SerialName("resolved_at") val resolvedAt: Long,       // System.currentTimeMillis()
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)

fun InventoryDeductionEntity.toDomain() = InventoryDeduction(
    id = id,
    vaccinationId = vaccinationId,
    vaccineId = vaccineId,
    vaccineName = vaccineName,
    batchId = batchId,
    quantity = quantity,
    status = status,
    errorMessage = errorMessage,
    resolvedAt = resolvedAt,
    createdBy = createdBy,
    updatedBy = updatedBy
)

fun com.neochildclinic.domain.model.InventoryDeduction.toEntity(
    batchId: String? = null, resolvedAt: Long = System.currentTimeMillis(), createdBy: String? = null, updatedBy: String? = null
) = InventoryDeductionEntity(
    id = id, vaccinationId = vaccinationId, vaccineId = vaccineId, vaccineName = vaccineName, batchId = this.batchId,
    quantity = quantity, status = status, errorMessage = errorMessage, resolvedAt = if (resolvedAt == 0L) this.resolvedAt else resolvedAt, createdBy = createdBy ?: this.createdBy, updatedBy = updatedBy ?: this.updatedBy
)
