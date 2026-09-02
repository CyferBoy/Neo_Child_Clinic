package com.neochildclinic.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A personal follow-up reminder about a vaccine requirement for a patient.
 *
 * This is intentionally independent of:
 *  - Next Vaccination / due-vaccination reminders ([ReminderEntity])
 *  - Actual vaccination records ([VaccinationEntity])
 *  - Financial records ([FinanceEntity])
 *
 * The [advanceAmount]/[advanceDate] fields are informational only - saving a
 * reminder must never create a payment/finance transaction, and completing a
 * vaccination or entering a payment must never change [status] here. Status
 * only ever changes via an explicit user action.
 */
@Serializable
@Entity(
    tableName = "personal_vaccine_reminders",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = VaccineEntity::class,
            parentColumns = ["id"],
            childColumns = ["vaccine_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("patient_id"),
        Index("vaccine_id"),
        Index("status"),
        Index("reminder_date")
    ]
)
data class PersonalReminderEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),

    @SerialName("patient_id") @ColumnInfo(name = "patient_id") val patientId: String? = null,

    // Snapshot contact details for non-saved patients, and the selected patient
    // phone/name for this reminder. Saved patients are still linked by patientId.
    @SerialName("patient_name") @ColumnInfo(name = "patient_name") val patientName: String,
    @SerialName("patient_phone") @ColumnInfo(name = "patient_phone") val patientPhone: String,

    // Null when the request is from a non-saved patient.
    // For saved patients this references the patient record.
    // Null when the request is for a general/unspecified ("Other") requirement
    // rather than a specific catalog vaccine.
    @SerialName("vaccine_id") @ColumnInfo(name = "vaccine_id") val vaccineId: String? = null,

    // Denormalized display label captured at creation time (the vaccine's brand
    // name, or "Other" for a non-catalog requirement) so the list/card UI never
    // needs to join against the vaccines table just to render a title, and so the
    // label survives even if the vaccine is later deleted (vaccine_id -> SET NULL).
    @SerialName("vaccine_label") @ColumnInfo(name = "vaccine_label") val vaccineLabel: String? = null,

    // Free-form personal note / requirement description.
    val note: String? = null,

    @SerialName("advance_received") @ColumnInfo(name = "advance_received") val advanceReceived: Boolean = false,
    @SerialName("advance_amount") @ColumnInfo(name = "advance_amount") val advanceAmount: Double? = null,
    @SerialName("advance_date") @ColumnInfo(name = "advance_date") val advanceDate: String? = null,

    @SerialName("reminder_date") @ColumnInfo(name = "reminder_date") val reminderDate: String? = null,

    // PersonalReminderStatus name: PENDING | READY | COMPLETED | CANCELLED
    val status: String = "PENDING",

    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: String = "",
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: String = "",
    @SerialName("completed_at") @ColumnInfo(name = "completed_at") val completedAt: String? = null,
    @SerialName("cancelled_at") @ColumnInfo(name = "cancelled_at") val cancelledAt: String? = null,

    @SerialName("is_synced") @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    @SerialName("updated_by") @ColumnInfo(name = "updated_by") val updatedBy: String? = null
)
