package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.neochildclinic.domain.model.Patient
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
    val patientClinicId: String = "",
    val name: String,
    val phone: String,
    val alternatePhone: String = "",
    val dob: String,
    val gender: String,
    val address: String = "",
    val registrationDate: String = "",
    
    // Structure Updates
    val guardianRelation: String? = null,
    val guardianPhone: String? = null,
    val attachments: String? = null, // JSON path or metadata

    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true
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
    updatedAt = updatedAt
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
    updatedAt = updatedAt,
    isSynced = isSynced
)
