package com.neochildclinic.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache for Widget display data to avoid heavy calculations during widget updates.
 */
@Entity(tableName = "widget_due_cache")
data class WidgetDueEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientName: String,
    val vaccineName: String,
    val dueDate: String,
    val isOverdue: Boolean
)
