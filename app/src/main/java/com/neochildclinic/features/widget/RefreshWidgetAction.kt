package com.neochildclinic.features.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.neochildclinic.core.utils.WidgetUtils

/**
 * Runs when the user taps the widget's refresh button. Reuses the same
 * WidgetUtils.updateWidget() path used elsewhere in the app after a vaccination is
 * recorded, etc. - it re-enqueues WidgetWorker, which recomputes the due list from
 * the local database and then updates the Glance widget once the fresh cache is
 * written (not just a bare redraw of whatever was already cached).
 */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WidgetUtils.updateWidget(context)
    }
}
