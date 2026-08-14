package com.neochildclinic.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReminderStatus {
    ACTIVE,
    COMPLETED,
    DISMISSED
}
