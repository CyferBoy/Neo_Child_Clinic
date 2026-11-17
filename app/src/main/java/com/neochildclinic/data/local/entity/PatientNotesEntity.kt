package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.neochildclinic.domain.model.PatientNote

@Serializable
@Entity(
    tableName = "patient_notes",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId"), Index("timestamp")]
)
data class PatientNotesEntity(
    @PrimaryKey @SerialName("id") val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("patient_id") val patientId: String,
    @SerialName("content") val content: String,
    @SerialName("author") val author: String,
    @SerialName("timestamp") val timestamp: String = com.neochildclinic.core.common.DateUtils.getCurrentIsoTimestamp(),
    @SerialName("is_synced") override val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
) : SyncableEntity

fun PatientNotesEntity.toDomain() = PatientNote(
    id = id,
    patientId = patientId,
    content = content,
    author = author,
    timestamp = timestamp
)
