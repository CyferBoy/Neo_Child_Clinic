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
        return try {
            val date = try {
                java.time.OffsetDateTime.parse(value).toLocalDate()
            } catch (_: Exception) {
                try {
                    java.time.LocalDateTime.parse(value).toLocalDate()
                } catch (_: Exception) {
                    java.time.LocalDate.parse(value)
                }
            }

            val currentYear = java.time.LocalDate.now().year
            val pattern = if (date.year == currentYear) "dd MMM" else "dd MMM yy"
            date.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
        } catch (_: Exception) {
            value
        }
    }
}
