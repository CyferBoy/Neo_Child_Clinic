package com.neochildclinic.core.notification

/**
 * Pure contract for reminder scheduling. Implementation resides in app.work.
 */
interface ReminderScheduler {
    suspend fun scheduleDailySummary()
    suspend fun schedulePersonalReminderNotifications()
    fun runNow()
}
