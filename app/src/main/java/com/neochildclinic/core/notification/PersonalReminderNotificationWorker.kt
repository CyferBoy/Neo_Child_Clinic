package com.neochildclinic.core.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.core.common.DateCategory
import com.neochildclinic.core.common.DateClassifier
import com.neochildclinic.data.local.database.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class PersonalReminderNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val reminders = database.personalReminderDao().getActiveReminders().first()
        for (r in reminders) {
            val date = r.reminderDate
            if (date.isNullOrBlank()) {
                notificationHelper.showPersonalReminderNotification(r.id, r.patientName, r.vaccineLabel ?: "Vaccine Requirement", r.patientPhone, undated = true)
                continue
            }
            when (val category = DateClassifier.classify(date)) {
                is DateCategory.Today -> {
                    notificationHelper.showPersonalReminderNotification(
                        r.id, r.patientName, r.vaccineLabel ?: "Vaccine Requirement", r.patientPhone
                    )
                }
                is DateCategory.Overdue -> {
                    notificationHelper.showPersonalReminderNotification(
                        r.id, r.patientName, r.vaccineLabel ?: "Vaccine Requirement", r.patientPhone,
                        overdueDays = category.days
                    )
                }
                else -> {
                    // Future date (Tomorrow or Future) - do not notify yet
                }
            }
        }
        return Result.success()
    }
}
