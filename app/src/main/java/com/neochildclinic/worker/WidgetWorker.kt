package com.neochildclinic.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.features.widget.VaccineWidget
import com.neochildclinic.data.local.entity.WidgetDueEntity
import com.neochildclinic.domain.repository.ReminderRepository
import com.neochildclinic.domain.repository.PatientRepository
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.core.utils.DateCategory
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
                
                // Show only day/month for the current running year; include the year for other years.
                val displayDate = formatWidgetDate(vacc.nextDueDate)

                WidgetDueEntity(
                    patientName = patient?.name ?: "Unknown",
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
            Result.failure()
        }
    }

    private fun formatWidgetDate(value: String): String {
        // The reminder's nextDueDate is stored in the app's normal date convention
        // (Constants.DATE_FORMAT, e.g. "15 June 2026"), not ISO - java.time.*Parse
        // never matched that, so this always silently fell through to the raw
        // value below and the year was never actually trimmed. PatientUtils.parseDate
        // already knows every format the app stores dates in, so reuse it here too.
        val date = PatientUtils.parseDate(value) ?: return value

        val dateYear = java.util.Calendar.getInstance().apply { time = date }.get(java.util.Calendar.YEAR)
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

        // Same year as today -> "15 June". Different year -> "15 June 25".
        val pattern = if (dateYear == currentYear) "d MMMM" else "d MMMM yy"
        return java.text.SimpleDateFormat(pattern, java.util.Locale.ENGLISH).format(date)
    }
}
