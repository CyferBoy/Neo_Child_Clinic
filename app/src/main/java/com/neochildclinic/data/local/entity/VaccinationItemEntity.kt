package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.VaccinationItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "vaccination_items",
    foreignKeys = [
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaccinationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VaccineEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaccineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VaccineBatchEntity::class,
            parentColumns = ["batchId"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vaccinationId"),
        Index("vaccineId"),
        Index("batchId")
    ]
)
data class VaccinationItemEntity(
    @PrimaryKey val id: String,
    @SerialName("vaccination_id") val vaccinationId: String,
    @SerialName("vaccine_id") val vaccineId: String,
    // Denormalized alongside vaccineId - the live vaccine catalog can change/be deleted
    // after this item is recorded, and the vaccine name/batch number the record was
    // administered under needs to survive that. Previously not stored at all, so every
    // consumer reading these per-item (Edit screen, patient card fallback, audit trail)
    // saw a blank name/number regardless of what was actually given.
    @SerialName("vaccine_name") val vaccineName: String = "",
    @SerialName("batch_id") val batchId: String,
    @SerialName("batch_number") val batchNumber: String = "",
    val quantity: Int = 1,
    val mrp: Double = 0.0,
    @SerialName("net_rate") val netRate: Double = 0.0,
    @SerialName("expiry_date") val expiryDate: String = ""
)

fun VaccinationItemEntity.toDomain() = VaccinationItem(
    id = id,
    vaccinationId = vaccinationId,
    vaccineId = vaccineId,
    vaccineName = vaccineName,
    batchId = batchId,
    batchNumber = batchNumber,
    quantity = quantity,
    mrp = mrp,
    netRate = netRate,
    expiryDate = expiryDate
)
