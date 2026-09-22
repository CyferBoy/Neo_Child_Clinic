package com.neochildclinic.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@HiltWorker
class PersonalReminderNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminders = database.personalReminderDao().getActiveReminders().first()
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH))
        for (r in reminders) {
            val date = r.reminderDate
            if (date.isNullOrBlank()) {
                notificationHelper.showPersonalReminderNotification(r.id, r.patientName, r.vaccineLabel ?: "Vaccine Requirement", r.patientPhone, undated = true)
                continue
            }
            when {
                date == today -> notificationHelper.showPersonalReminderNotification(r.id, r.patientName, r.vaccineLabel ?: "Vaccine Requirement", r.patientPhone)
                date < today -> {
                    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
                    val days = try {
                        ChronoUnit.DAYS.between(LocalDate.parse(date, fmt), LocalDate.parse(today, fmt)).toInt()
                    } catch (_: Exception) { 0 }
                    notificationHelper.showPersonalReminderNotification(r.id, r.patientName, r.vaccineLabel ?: "Vaccine Requirement", r.patientPhone, overdueDays = days.coerceAtLeast(1))
                }
            }
        }
        return Result.success()
    }
}
