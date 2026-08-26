package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "consultation_todos",
    indices = [
        Index("todo_date"),
        Index("status"),
        Index("patient_id")
    ]
)
data class ConsultationTodoEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("patient_id") @ColumnInfo(name = "patient_id") val patientId: String? = null,
    val name: String,
    val mobile: String,
    val address: String,
    @SerialName("todo_date") @ColumnInfo(name = "todo_date") val todoDate: String,
    val status: String = "PENDING",
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: String = "",
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: String = "",
    @SerialName("is_synced") @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)

@Serializable
@Entity(
    tableName = "vaccination_todos",
    indices = [
        Index("todo_date"),
        Index("status"),
        Index("patient_id")
    ]
)
data class VaccinationTodoEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    @SerialName("patient_id") @ColumnInfo(name = "patient_id") val patientId: String? = null,
    val name: String,
    val mobile: String,
    @SerialName("vaccine_names") @ColumnInfo(name = "vaccine_names") val vaccineNames: String,
    val address: String,
    @SerialName("todo_date") @ColumnInfo(name = "todo_date") val todoDate: String,
    val status: String = "PENDING",
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: String = "",
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: String = "",
    @SerialName("is_synced") @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)
