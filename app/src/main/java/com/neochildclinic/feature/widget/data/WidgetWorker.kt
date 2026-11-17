package com.neochildclinic.feature.widget.data
import com.neochildclinic.core.common.DateUtils

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.feature.widget.presentation.VaccineWidget
import com.neochildclinic.data.local.entity.WidgetDueEntity
import com.neochildclinic.feature.reminder.domain.repository.ReminderRepository
import com.neochildclinic.core.common.DateClassifier
import com.neochildclinic.core.common.DateCategory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Background worker to pre-calculate widget data.
 * Offloads heavy business logic from the Widget's provideGlance.
 */
@HiltWorker
class WidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepository: ReminderRepository,
    private val database: AppDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dueList = reminderRepository.getDueList().first()
                .sortedWith(compareBy(DateClassifier.dueDateComparator()) { it.nextDueDate })

            // Names come from one projection query over just the due row's patient ids
            // (PatientDao.getNamesByIds), not from the whole patients table.
            val names = database.patientDao()
                .getNamesByIds(dueList.map { it.patientId }.distinct())
                .associate { it.id to it.name }

            val widgetItems = dueList.map { vacc ->
                val name = names[vacc.patientId] ?: "Unknown"
                val category = DateClassifier.classify(vacc.nextDueDate)
                
                // Show only day/month for the current running year; include the year for other years.
                val displayDate = DateUtils.formatDateWithOptionalYear(vacc.nextDueDate)

                WidgetDueEntity(
                    patientName = name,
                    vaccineName = vacc.nxtVaccineNames.joinToString(", "),
                    dueDate = displayDate,
                    isOverdue = category is DateCategory.Overdue
                )
            }

            database.widgetDueDao().refreshCache(widgetItems)

            // Update the Glance widget only after the cache has been populated.
            // Previously WidgetUtils refreshed the widget immediately after enqueueing
            // this worker, so the widget could read the old/empty cache and display
            // "No upcoming vaccinations".
            val manager = GlanceAppWidgetManager(applicationContext)
            val ids = manager.getGlanceIds(VaccineWidget::class.java)
            ids.forEach { id ->
                VaccineWidget().update(applicationContext, id)
            }

            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure()
        }
    }
}
