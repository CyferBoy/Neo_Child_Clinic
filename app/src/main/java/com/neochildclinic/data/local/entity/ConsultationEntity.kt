package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.Consultation
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "consultations",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId"), Index("date")]
)
data class ConsultationEntity(
    @PrimaryKey val id: String,
    val patientId: String,
    val date: String,
    val amount: Double,
    val notes: String = "",
    val nextFollowUpDate: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true
)

fun ConsultationEntity.toDomain() = Consultation(
    id = id,
    patientId = patientId,
    date = date,
    amount = amount,
    notes = notes,
    nextFollowUpDate = nextFollowUpDate
)

fun Consultation.toEntity(isSynced: Boolean = true) = ConsultationEntity(
    id = id,
    patientId = patientId,
    date = date,
    amount = amount,
    notes = notes,
    nextFollowUpDate = nextFollowUpDate,
    isSynced = isSynced
)
