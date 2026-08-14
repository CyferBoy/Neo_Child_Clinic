package com.neochildclinic.core.utils

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.neochildclinic.features.widget.VaccineWidget
import com.neochildclinic.worker.WidgetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUtils {
    /**
     * Triggers a widget update by first refreshing the data cache via WidgetWorker.
     */
    fun updateWidget(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<WidgetWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        // After worker finishes, the widget will be updated.
        // For immediate visual feedback of 'refreshing', we could update state here too.
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val manager = GlanceAppWidgetManager(context)
                val ids = manager.getGlanceIds(VaccineWidget::class.java)
                ids.forEach { id ->
                    VaccineWidget().update(context, id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}