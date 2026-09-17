package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
    indices = [Index("vaccinationId"), Index("status")]
)
data class InventoryDeductionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vaccinationId: String,
    val vaccineId: String,
    val vaccineName: String,
    val batchId: String?,      // null until resolved/attempted
    val quantity: Int,
    val status: String,        // "COMPLETED" or "FAILED"
    val errorMessage: String?,
    val resolvedAt: Long,       // System.currentTimeMillis()
    @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)
