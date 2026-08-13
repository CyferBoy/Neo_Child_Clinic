package com.neochildclinic.data.local.entity

import androidx.room.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Local Room Entity for reminders table.
 */
@TypeConverters(com.neochildclinic.data.local.database.Converters::class)
@Entity(
    tableName = "reminders",
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
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val serverId: Long? = null,
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
    @ColumnInfo(name = "nxt_vaccine_id")
    val nxtVaccineId: List<String>? = null,
    
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
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RemoteReminder(
    // The Supabase client is configured globally with encodeDefaults = true, which would
    // otherwise serialize a null id as an explicit "id": null in the insert payload. Since
    // reminders.id is a server-generated BIGSERIAL NOT NULL primary key, that explicit null
    // overrides the column default and trips the not-null constraint on CREATE. Forcing this
    // one field to be omitted when null (its default) lets Postgres apply nextval() itself.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
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
    @SerialName("nxt_vaccine_id") val nxtVaccineId: List<String>? = null,
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
    id = serverId,
    patientId = patientId,
    originalVisitId = originalVisitId,
    vaccineName = vaccineName,
    dueDate = dueDate,
    status = status,
    priority = priority,
    reminderEnabled = reminderEnabled,
    category = category,
    type = type,
    nxtVaccineId = nxtVaccineId,
    notes = notes,
    completionDate = completionDate,
    performedBy = performedBy,
    dismissalDate = dismissalDate,
    dismissalReason = dismissalReason,
    createdAt = if (createdAt.isEmpty()) null else createdAt,
    updatedAt = if (updatedAt.isEmpty()) null else updatedAt,
    isSynced = true
)

fun RemoteReminder.toLocal(localId: String? = null) = ReminderEntity(
    id = localId ?: java.util.UUID.randomUUID().toString(),
    serverId = id,
    patientId = patientId,
    originalVisitId = originalVisitId,
    vaccineName = vaccineName,
    dueDate = dueDate,
    status = status,
    priority = priority,
    reminderEnabled = reminderEnabled,
    category = category,
    type = type,
    nxtVaccineId = nxtVaccineId,
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
