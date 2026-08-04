package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_deductions")
data class InventoryDeductionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vaccinationId: String,
    val vaccineId: String,
    val vaccineName: String,
    val batchId: String?,      // null until resolved/attempted
    val quantity: Int,
    val status: String,        // "COMPLETED" or "FAILED"
    val errorMessage: String?,
    val resolvedAt: Long       // System.currentTimeMillis()
)
