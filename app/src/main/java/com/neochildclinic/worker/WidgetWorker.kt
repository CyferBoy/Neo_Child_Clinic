package com.neochildclinic.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.WidgetDueEntity
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.core.utils.DateCategory
import com.neochildclinic.features.widget.VaccineWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlin.text.get

/**
 * Background worker to pre-calculate widget data.
 * Offloads heavy business logic from the Widget's provideGlance.
 */
@HiltWorker
class WidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepository: ReminderRepository,
    private val patientRepository: PatientRepository,
    private val database: AppDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dueList = reminderRepository.getDueList().first()
                .sortedBy { DateClassifier.getSortWeight(it.nextDueDate) }
            
            val patients = patientRepository.allPatients.first().associateBy { it.id }
            
            val widgetItems = dueList.map { vacc ->
                val patient = patients[vacc.patientId]
                val category = DateClassifier.classify(vacc.nextDueDate)
                
                // Match original MMM d format for future dates, specific labels for others
                val displayDate = when (category) {
                    is DateCategory.Today -> "Today"
                    is DateCategory.Tomorrow -> "Tomorrow"
                    is DateCategory.Overdue -> PatientUtils.formatDateForDisplay(vacc.nextDueDate)
                    is DateCategory.Future -> category.dateStr
                }

                WidgetDueEntity(
                    patientName = patient?.name ?: "Unknown",
                    vaccineName = vacc.nxtVaccineNames.joinToString(", "),
                    dueDate = displayDate,
                    isOverdue = category is DateCategory.Overdue
                )
            }

            database.widgetDueDao().refreshCache(widgetItems)

            // Refresh every widget instance only after the cache has been updated.
            // This prevents the widget from rendering stale data while the worker
            // is still rebuilding the Due cache.
            val manager = GlanceAppWidgetManager(applicationContext)
            val ids = manager.getGlanceIds(VaccineWidget::class.java)
            ids.forEach { id ->
                VaccineWidget().update(applicationContext, id)
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
