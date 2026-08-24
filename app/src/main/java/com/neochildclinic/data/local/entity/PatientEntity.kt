package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.Patient
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
    @PrimaryKey val id: String,
    @SerialName("patient_clinic_id") val patientClinicId: String? = null,
    val name: String,
    val phone: String,
    @SerialName("alternate_phone") val alternatePhone: String? = null,
    val dob: String,
    val gender: String,
    val address: String? = null,
    @SerialName("registration_date") val registrationDate: String? = null,
    
    val attachments: String? = null, // JSON path or metadata

    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = true,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)

fun PatientEntity.toPatient() = Patient(
    id = id,
    patientClinicId = patientClinicId,
    name = name,
    phone = phone,
    alternatePhone = alternatePhone,
    dob = dob,
    gender = gender,
    address = address,
    registrationDate = registrationDate,
    updatedAt = updatedAt,
    attachments = attachments,
    createdBy = createdBy,
    updatedBy = updatedBy
)

fun Patient.toEntity(isSynced: Boolean = true) = PatientEntity(
    id = id,
    patientClinicId = patientClinicId,
    name = name,
    phone = phone,
    alternatePhone = alternatePhone,
    dob = dob,
    gender = gender,
    address = address,
    registrationDate = registrationDate,
    updatedAt = if (updatedAt.isNullOrEmpty()) com.neochildclinic.core.utils.PatientUtils.getCurrentIsoTimestamp() else updatedAt,
    isSynced = isSynced,
    attachments = attachments,
    createdBy = createdBy,
    updatedBy = updatedBy
)
