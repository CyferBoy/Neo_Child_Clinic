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
        Index(value = ["isSynced"]), 
        Index(value = ["isDeleted"])
    ]
)
data class PatientEntity(
    @PrimaryKey val id: String,
    val patientClinicId: String = "",
    val name: String,
    val parentName: String = "",
    val phone: String,
    val alternatePhone: String = "",
    val dob: String,
    val gender: String,
    val village: String = "",
    val address: String = "",
    val registrationDate: String = "",
    
    // Structure Updates
    val notes: String? = null,
    val guardianRelation: String? = null,
    val guardianPhone: String? = null,
    val attachments: String? = null, // JSON path or metadata

    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true,
    val isDeleted: Boolean = false
)

fun PatientEntity.toPatient() = Patient(
    id = id,
    patientClinicId = patientClinicId,
    name = name,
    parentName = parentName,
    phone = phone,
    alternatePhone = alternatePhone,
    dob = dob,
    gender = gender,
    village = village,
    address = address,
    registrationDate = registrationDate,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun Patient.toEntity(isSynced: Boolean = true) = PatientEntity(
    id = id,
    patientClinicId = patientClinicId,
    name = name,
    parentName = parentName,
    phone = phone,
    alternatePhone = alternatePhone,
    dob = dob,
    gender = gender,
    village = village,
    address = address,
    registrationDate = registrationDate,
    updatedAt = updatedAt,
    isSynced = isSynced,
    isDeleted = isDeleted
)
