package com.neochildclinic.domain.model

/** Dashboard Due-section counts, produced by [com.neochildclinic.data.repository.ReminderDueListProcessor]. */
data class ReminderStats(
    val dueToday: Int = 0,
    val dueTomorrow: Int = 0,
    val overdue: Int = 0,
    val completedToday: Int = 0,
    val dismissedToday: Int = 0,
    val notificationsSentToday: Int = 0
)