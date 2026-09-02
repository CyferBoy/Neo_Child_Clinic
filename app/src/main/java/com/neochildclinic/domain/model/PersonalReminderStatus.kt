package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

/**
 * Status lifecycle for a Personal Vaccine Reminder.
 *
 * Normal flow: PENDING -> READY -> COMPLETED
 * Manual-only side paths: PENDING -> CANCELLED, READY -> PENDING
 *
 * IMPORTANT: COMPLETED must only ever be set by an explicit user action
 * ("Mark as Completed"). Nothing in the app should transition a reminder
 * to COMPLETED automatically (not vaccination records, not payments, not
 * inventory changes, not the reminder date arriving).
 */
@Serializable
enum class PersonalReminderStatus {
    PENDING,
    READY,
    COMPLETED,
    CANCELLED;

    val displayName: String
        get() = when (this) {
            PENDING -> "Pending"
            READY -> "Ready"
            COMPLETED -> "Completed"
            CANCELLED -> "Cancelled"
        }

    companion object {
        fun fromRaw(raw: String): PersonalReminderStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}
