package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Legacy support for ReminderEntity.
 * Maps to unified reminder_states table.
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
        Index(value = ["patientId", "originalVisitId", "vaccineName"], unique = true),
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
    val vaccinationSource: String? = null,
    val notes: String? = null,
    
    // Extended fields from unified state
    val reminderDate: String? = null,
    val completionDate: Long? = null,
    val performedBy: String? = null,
    val dismissalDate: Long? = null,
    val dismissalReason: String? = null,
    val externalDate: String? = null,
    val source: String? = null,
    
    val lastReminderTime: Long = 0,
    val notificationSent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

// Legacy alias for type safety during transition
typealias DueReminderEntity = ReminderEntity
typealias CompletedReminderEntity = ReminderEntity
typealias DismissedReminderEntity = ReminderEntity
typealias ExternalReminderEntity = ReminderEntity
