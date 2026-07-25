package com.clinic.neochild.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.clinic.neochild.domain.model.BatchStatus
import com.clinic.neochild.domain.model.Vaccine

@Entity(tableName = "vaccines")
data class VaccineEntity(
    @PrimaryKey val id: String,
    val type: String,
    val brandName: String,
    val companyName: String,
    
    // Structure Updates
    val manufacturer: String? = null,
    val category: String? = null, // e.g. Mandatory, Optional, Adult
    val doseSchedule: String? = null, // JSON
    val storageDetails: String? = null,
    val mrp: Double = 0.0,
    val netRate: Double = 0.0,
    
    val lastUpdated: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "vaccine_batches",
    foreignKeys = [
        ForeignKey(
            entity = VaccineEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaccineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("vaccineId"), Index("batchNumber")]
)
data class VaccineBatchEntity(
    @PrimaryKey val batchId: String,
    val vaccineId: String,
    val batchNumber: String,
    val manufacturer: String,
    val purchaseDate: String,
    val expiryDate: String,
    val purchaseQuantity: Int,
    val remainingQuantity: Int, // Cached for performance, but module calculates from source
    
    // Detailed tracking
    val reservedQuantity: Int = 0,
    val usedQuantity: Int = 0,
    val wastedQuantity: Int = 0,
    val borrowedQuantity: Int = 0,

    val supplier: String,
    val purchaseCost: Double,
    val sellingPrice: Double,
    val status: String = BatchStatus.ACTIVE.name,
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "inventory_transactions",
    indices = [Index("vaccineId"), Index("batchId"), Index("vaccinationId")]
)
data class InventoryTransactionEntity(
    @PrimaryKey(autoGenerate = true) val transactionId: Long = 0,
    val vaccineId: String,
    val batchId: String,
    val patientId: String? = null,
    val vaccinationId: String? = null,
    val transactionType: String, // InventoryTransactionType
    val quantity: Int, 
    val previousQuantity: Int,
    val currentQuantity: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val user: String,
    val notes: String? = null,
    
    // Detailed tracking
    val status: String = "COMPLETED", // InventoryStatus
    val failureReason: String? = null,
    val processedAt: Long? = null,
    val processedBy: String? = null,
    val isSynced: Boolean = false
)

// Mappers for compatibility
fun VaccineEntity.toVaccine(totalStock: Int = 0) = Vaccine(
    id = id,
    type = type,
    brandName = brandName,
    companyName = companyName,
    stock = totalStock,
    mrp = mrp,
    netRate = netRate
)
