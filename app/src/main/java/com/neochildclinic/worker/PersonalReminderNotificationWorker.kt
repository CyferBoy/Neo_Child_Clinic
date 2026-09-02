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
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class PersonalReminderNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminders = database.personalReminderDao().getActiveReminders().first()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
        for (r in reminders) {
            val date = r.reminderDate
            if (date.isNullOrBlank()) {
                notificationHelper.showPersonalReminderNotification(r.id, r.patientName, r.vaccineLabel ?: "Vaccine Requirement", r.patientPhone, undated = true)
                continue
            }
            when {
                date == today -> notificationHelper.showPersonalReminderNotification(r.id, r.patientName, r.vaccineLabel ?: "Vaccine Requirement", r.patientPhone)
                date < today -> {
                    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    parser.isLenient = false
                    val days = try {
                        ((parser.parse(today)!!.time - parser.parse(date)!!.time) / 86_400_000L).toInt()
                    } catch (_: Exception) { 0 }
                    notificationHelper.showPersonalReminderNotification(r.id, r.patientName, r.vaccineLabel ?: "Vaccine Requirement", r.patientPhone, overdueDays = days.coerceAtLeast(1))
                }
            }
        }
        return Result.success()
    }
}
