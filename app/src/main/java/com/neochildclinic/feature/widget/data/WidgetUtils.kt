package com.neochildclinic.feature.widget.data

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.neochildclinic.feature.widget.presentation.VaccineWidget

object WidgetUtils {
    /**
     * Refreshes the widget data cache in the background.
     * WidgetWorker updates the actual Glance widget after the cache is written.
     */
    fun updateWidget(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<WidgetWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "vaccine_widget_refresh",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    /**
     * Re-renders the Glance widget UI without querying remote network or reloading the database cache.
     * Useful when system configuration (dark/light theme) changes.
     */
    suspend fun updateGlanceWidgetsOnly(context: Context) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(VaccineWidget::class.java)
            ids.forEach { id ->
                VaccineWidget().update(context, id)
            }
        } catch (_: Exception) {
            // Widget update failed (non-fatal, e.g. no widgets placed on home screen)
        }
    }
}
