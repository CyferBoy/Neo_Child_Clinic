package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "patients",
    indices = [
        Index(value = ["patientClinicId"], unique = true),
        Index(value = ["name"]),
        Index(value = ["phone"]),
        Index(value = ["isSynced"])
    ]
)
data class PatientEntity(
    @PrimaryKey val id: String = "",
    @SerialName("patient_clinic_id") val patientClinicId: String? = null,
    val name: String = "",
    val phone: String = "",
    @SerialName("alternate_phone") val alternatePhone: String? = null,
    val dob: String = "",
    val gender: String = "",
    val address: String? = null,
    @SerialName("registration_date") val registrationDate: String? = null,

    val attachments: String? = null, // JSON path or metadata

    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null,
    @SerialName("is_deleted") @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
    @SerialName("deleted_by") @ColumnInfo(name = "deleted_by") val deletedBy: String? = null
)
