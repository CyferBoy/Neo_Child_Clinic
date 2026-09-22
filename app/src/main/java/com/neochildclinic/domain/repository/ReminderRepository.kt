package com.neochildclinic.domain.repository

import com.neochildclinic.data.repository.ReminderRepositoryImpl

typealias ReminderRepository = ReminderRepositoryImpl

data class ReminderStats(
    val dueToday: Int = 0,
    val dueTomorrow: Int = 0,
    val overdue: Int = 0,
    val completedToday: Int = 0,
    val dismissedToday: Int = 0,
    val notificationsSentToday: Int = 0
)
