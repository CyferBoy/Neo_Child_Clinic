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
    @SerialName("batch_id") val batchId: String,
    val quantity: Int = 1,
    val mrp: Double = 0.0,
    @SerialName("net_rate") val netRate: Double = 0.0,
    @SerialName("expiry_date") val expiryDate: String = ""
)

fun VaccinationItemEntity.toDomain() = VaccinationItem(
    id = id,
    vaccinationId = vaccinationId,
    vaccineId = vaccineId,
    batchId = batchId,
    quantity = quantity,
    mrp = mrp,
    netRate = netRate,
    expiryDate = expiryDate
)
