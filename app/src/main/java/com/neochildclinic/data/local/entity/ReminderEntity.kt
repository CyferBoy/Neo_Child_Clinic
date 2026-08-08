package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Local Room Entity for reminder_states table.
 */
@Entity(
    tableName = "reminder_states",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["patientId", "originalVisitId", "vaccineName", "type"], unique = true),
        Index("status"),
        Index("dueDate")
    ]
)
@Serializable
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: String,
    val originalVisitId: String,
    val vaccineName: String,
    val dueDate: String,
    val status: String,
    val priority: String = "NORMAL",
    val reminderEnabled: Boolean = true,
    val category: String = "VACCINATION",

    // Due Vaccination "Type" (mandatory on the Add Vaccination screen, e.g. "Booster").
    // Kept separate from `category` (which distinguishes VACCINATION vs other reminder
    // sources) and separate from `vaccineName` -- Type is never inferred from the vaccine.
    val type: String = "",

    // Comma-separated vaccine catalog IDs for the vaccine(s) selected alongside Type
    // (optional -- may be blank/null if no vaccine was selected). Kept individually so
    // they remain available for future editing and sync, matching the comma-separated
    // convention already used for list-like columns elsewhere (e.g. patient_visits.vaccine_ids).
    val vaccinationIds: String? = null,
    
    // Local-only or derived fields
    @Transient val vaccinationSource: String? = null,
    @Transient val reminderDate: String? = null,
    @Transient val externalDate: String? = null,
    @Transient val source: String? = null,
    @Transient val lastReminderTime: Long = 0,
    @Transient val notificationSent: Boolean = false,
    
    val notes: String? = null,
    val completionDate: String? = null, // Changed to String? for TEXT compatibility
    val performedBy: String? = null,
    val dismissalDate: String? = null, // Changed to String? for TEXT compatibility
    val dismissalReason: String? = null,
    
    val createdAt: String = "",
    val updatedAt: String = "",
    val isSynced: Boolean = false
)

/**
 * Data Transfer Object for Supabase reminders table.
 */
@Serializable
data class RemoteReminder(
    val id: Long? = null,
    @SerialName("patient_id") val patientId: String,
    @SerialName("original_visit_id") val originalVisitId: String,
    @SerialName("vaccine_name") val vaccineName: String,
    @SerialName("due_date") val dueDate: String,
    val status: String,
    val priority: String = "NORMAL",
    @SerialName("reminder_enabled") val reminderEnabled: Boolean = true,
    val category: String = "VACCINATION",
    val type: String = "",
    @SerialName("vaccination_ids") val vaccinationIds: String? = null,
    val notes: String? = null,
    @SerialName("completion_date") val completionDate: String? = null,
    @SerialName("performed_by") val performedBy: String? = null,
    @SerialName("dismissal_date") val dismissalDate: String? = null,
    @SerialName("dismissal_reason") val dismissalReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_synced") val isSynced: Boolean = true
)

fun ReminderEntity.toRemote() = RemoteReminder(
    patientId = patientId,
    originalVisitId = originalVisitId,
    vaccineName = vaccineName,
    dueDate = dueDate,
    status = status,
    priority = priority,
    reminderEnabled = reminderEnabled,
    category = category,
    type = type,
    vaccinationIds = vaccinationIds,
    notes = notes,
    completionDate = completionDate,
    performedBy = performedBy,
    dismissalDate = dismissalDate,
    dismissalReason = dismissalReason,
    createdAt = if (createdAt.isEmpty()) null else createdAt,
    updatedAt = if (updatedAt.isEmpty()) null else updatedAt,
    isSynced = true
)

fun RemoteReminder.toLocal(localId: Long = 0) = ReminderEntity(
    id = localId,
    patientId = patientId,
    originalVisitId = originalVisitId,
    vaccineName = vaccineName,
    dueDate = dueDate,
    status = status,
    priority = priority,
    reminderEnabled = reminderEnabled,
    category = category,
    type = type,
    vaccinationIds = vaccinationIds,
    notes = notes,
    completionDate = completionDate,
    performedBy = performedBy,
    dismissalDate = dismissalDate,
    dismissalReason = dismissalReason,
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
    isSynced = true
)

// Legacy aliases for type safety during transition
typealias DueReminderEntity = ReminderEntity
typealias CompletedReminderEntity = ReminderEntity
typealias DismissedReminderEntity = ReminderEntity
typealias ExternalReminderEntity = ReminderEntity
