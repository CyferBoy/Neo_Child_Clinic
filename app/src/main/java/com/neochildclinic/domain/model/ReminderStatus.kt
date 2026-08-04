package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReminderStatus {
    ACTIVE,      // System calculated, no manual action yet
    COMPLETED,   // Vaccination given in this clinic
    RESCHEDULED, // Date manually changed by staff
    EXTERNAL,    // Given at another facility
    DISMISSED,   // Staff manually hid the reminder
    MISSED,      // Patient never showed up (logic derived)
    SENT         // Notification was successfully shown
}
