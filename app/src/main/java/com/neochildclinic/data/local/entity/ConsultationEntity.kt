package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.Consultation
import kotlinx.serialization.SerialName
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
        ),
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId"), Index("visitId"), Index("date")]
)
data class ConsultationEntity(
    @PrimaryKey val id: String,
    @SerialName("visit_id") val visitId: String = "",
    @SerialName("patient_id") val patientId: String,
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("doctor_name") val doctorName: String = "",
    val date: String,
    val amount: Double,
    @SerialName("cash_amount") val cashAmount: Double = 0.0,
    @SerialName("online_amount") val onlineAmount: Double = 0.0,
    val problem: String = "",
    val notes: String = "", // Kept for notes if needed, spec uses problem
    @SerialName("next_follow_up_date") val nextFollowUpDate: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = true
)

fun ConsultationEntity.toDomain() = Consultation(
    id = id,
    visitId = visitId,
    patientId = patientId,
    doctorId = doctorId,
    doctorName = doctorName,
    date = date,
    amount = amount,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    problem = problem,
    notes = notes,
    nextFollowUpDate = nextFollowUpDate,
    updatedAt = updatedAt ?: ""
)

fun Consultation.toEntity(isSynced: Boolean = true) = ConsultationEntity(
    id = id,
    visitId = visitId,
    patientId = patientId,
    doctorId = doctorId,
    doctorName = doctorName,
    date = date,
    amount = amount,
    cashAmount = cashAmount,
    onlineAmount = onlineAmount,
    problem = problem,
    notes = notes,
    nextFollowUpDate = nextFollowUpDate,
    createdAt = if (updatedAt.isEmpty()) null else updatedAt,
    updatedAt = if (updatedAt.isEmpty()) null else updatedAt,
    isSynced = isSynced
)
