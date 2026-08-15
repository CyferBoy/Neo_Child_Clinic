package com.neochildclinic.core.utils

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.neochildclinic.worker.WidgetWorker

object WidgetUtils {
    /**
     * Refreshes the widget data cache in the background.
     * WidgetWorker updates the actual Glance widget after the cache is written.
     */
    fun updateWidget(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<WidgetWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
