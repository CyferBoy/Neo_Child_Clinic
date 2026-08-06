package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.BatchStatus
import com.neochildclinic.domain.model.Vaccine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "vaccines")
data class VaccineEntity(
    @PrimaryKey val id: String,
    val type: String,
    @SerialName("brand_name") val brandName: String,
    @SerialName("company_name") val companyName: String,
    
    // Structure Updates
    val manufacturer: String? = null,
    val category: String? = null, // e.g. Mandatory, Optional, Adult
    @SerialName("dose_schedule") val doseSchedule: String? = null,
    @SerialName("storage_details") val storageDetails: String? = null,
    val mrp: Double = 0.0,
    @SerialName("net_rate") val netRate: Double = 0.0,
    
    @SerialName("last_updated") val lastUpdated: String = ""
)

@Serializable
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
    @PrimaryKey @SerialName("id") val batchId: String,
    @SerialName("vaccine_id") val vaccineId: String,
    @SerialName("batch_number") val batchNumber: String,
    val manufacturer: String,
    @SerialName("purchase_date") val purchaseDate: String,
    @SerialName("expiry_date") val expiryDate: String,
    @SerialName("purchase_quantity") val purchaseQuantity: Int,
    @SerialName("remaining_quantity") val remainingQuantity: Int,
    
    // Detailed tracking
    @SerialName("reserved_quantity") val reservedQuantity: Int = 0,
    @SerialName("used_quantity") val usedQuantity: Int = 0,
    @SerialName("wasted_quantity") val wastedQuantity: Int = 0,
    @SerialName("borrowed_quantity") val borrowedQuantity: Int = 0,

    val supplier: String,
    @SerialName("purchase_cost") val purchaseCost: Double,
    @SerialName("selling_price") val sellingPrice: Double,
    val status: String = BatchStatus.ACTIVE.name,
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
@Entity(
    tableName = "inventory_transactions",
    indices = [Index("vaccineId"), Index("batchId"), Index("vaccinationId")]
)
data class InventoryTransactionEntity(
    @PrimaryKey @SerialName("id") val transactionId: String = java.util.UUID.randomUUID().toString(),
    @SerialName("vaccine_id") val vaccineId: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("patient_id") val patientId: String? = null,
    @SerialName("vaccination_id") val vaccinationId: String? = null,
    @SerialName("transaction_type") val transactionType: String, // InventoryTransactionType
    val quantity: Int, 
    @SerialName("previous_quantity") val previousQuantity: Int,
    @SerialName("current_quantity") val currentQuantity: Int,
    val timestamp: String = "",
    val user: String,
    val notes: String? = null,
    
    // Detailed tracking - Local Only
    @kotlinx.serialization.Transient val status: String = "COMPLETED", // InventoryStatus
    @kotlinx.serialization.Transient @SerialName("failure_reason") val failureReason: String? = null,
    @kotlinx.serialization.Transient @SerialName("processed_at") val processedAt: String? = null,
    @kotlinx.serialization.Transient @SerialName("processed_by") val processedBy: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = false
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
