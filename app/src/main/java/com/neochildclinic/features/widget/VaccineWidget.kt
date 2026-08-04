package com.neochildclinic.features.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import androidx.glance.action.ActionParameters
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.entity.WidgetDueEntity
import com.neochildclinic.app.MainActivity
import kotlinx.coroutines.flow.first

class VaccineWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        val OPACITY_KEY = floatPreferencesKey("widget_opacity")
        private const val DEFAULT_OPACITY = 0.8f
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        // Use PRE-CALCULATED data from cache
        val dueItems = database.widgetDueDao().getDueItems().first()

        provideContent {
            val prefs = currentState<Preferences>()
            val opacity = prefs[OPACITY_KEY] ?: DEFAULT_OPACITY
            
            GlanceTheme {
                WidgetContent(context, dueItems, opacity)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        items: List<WidgetDueEntity>,
        opacity: Float
    ) {
        val resolvedBgColor = GlanceTheme.colors.surface.getColor(context).copy(alpha = opacity)
        val textColorProvider = GlanceTheme.colors.onSurface
        
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(resolvedBgColor)
                .cornerRadius(24.dp)
                .padding(9.dp)
                .clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(
                            ActionParameters.Key<Boolean>("OPEN_DUE_TAB") to true
                        )
                    )
                )
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Due Vaccine",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColorProvider
                    )
                )
            }

            if (items.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No upcoming vaccinations",
                        style = TextStyle(fontSize = 14.sp, color = textColorProvider)
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items) { item ->
                        VaccineRow(item, textColorProvider)
                    }
                }
            }
        }
    }

    @Composable
    private fun VaccineRow(item: WidgetDueEntity, textColorProvider: ColorProvider) {
        val overdueColor = GlanceTheme.colors.error
        val otherDateColor = GlanceTheme.colors.onSurfaceVariant

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.patientName,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(fontSize = 12.sp, color = textColorProvider),
                maxLines = 1
            )
            
            Text(
                text = item.dueDate,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = if (item.isOverdue) overdueColor else otherDateColor,
                    fontWeight = if (item.isOverdue) FontWeight.Bold else FontWeight.Normal
                )
            )
        }
    }
}
