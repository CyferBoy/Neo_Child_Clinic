package com.neochildclinic.features.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionRunCallback
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.ColorProvider
import com.neochildclinic.R
import com.neochildclinic.app.MainActivity
import com.neochildclinic.data.local.database.AppDatabase
import com.neochildclinic.data.local.dao.WidgetDueDao
import com.neochildclinic.data.local.entity.WidgetDueEntity
import kotlinx.coroutines.flow.first

class VaccineWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        val OPACITY_KEY = floatPreferencesKey("widget_opacity")
        val THEME_KEY = stringPreferencesKey("widget_theme")
        private const val DEFAULT_OPACITY = 0.85f
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val database = AppDatabase.getDatabase(context)
        val widgetDueDao = database.widgetDueDao()
        val dataSource = object : WidgetLocalDataSource(widgetDueDao) {
            override val dueItemsFlow: Flow<List<WidgetDueEntity>>
                get() = widgetDueDao.getDueItems()

            override suspend fun getDueItemsFirst(): List<WidgetDueEntity> = widgetDueDao.getDueItems().first()
        }

        val dueItems = dataSource.getDueItemsFirst().first()

        provideContent {
            val prefs = currentState<Preferences>()
            val opacity = prefs[OPACITY_KEY] ?: DEFAULT_OPACITY
            val theme = VaccineWidgetTheme.fromKey(prefs[THEME_KEY])
            WidgetContent(context, dueItems, opacity, theme)
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        items: List<WidgetDueEntity>,
        opacity: Float,
        theme: VaccineWidgetTheme
    ) {
        val colors = theme.colors(context)
        val resolvedBgColor = colors.background.copy(alpha = opacity)
        val textColorProvider = ColorProvider(colors.primaryText)
        val secondaryTextProvider = ColorProvider(colors.secondaryText)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(resolvedBgColor)
                .cornerRadius(24.dp)
                .padding(6.dp)
                .clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(
                            ActionParameters.Key<Boolean>("OPEN_DUE_TAB") to true
                        )
                    )
                )
        ) {
            // Header: Centered Title
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Due Vaccination",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColorProvider
                    )
                )
            }
            Divider(secondaryTextProvider)

            // Content: Takes available space
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (items.isEmpty()) {
                    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No upcoming vaccinations",
                            style = TextStyle(fontSize = 15.sp, color = secondaryTextProvider)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(top = 4.dp)) {
                        items(items) { item ->
                            VaccineRow(item, textColorProvider, colors.accent)
                        }
                    }
                }
            }

            // Footer: Centered Refresh Button
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Refresh",
                    colorFilter = androidx.glance.ColorFilter.tint(textColorProvider),
                    modifier = GlanceModifier
                        .size(20.dp)
                        .clickable(actionRunCallback<RefreshWidgetAction>())
                )
            }
        }
    }

    @Composable
    private fun VaccineRow(
        item: WidgetDueEntity,
        textColorProvider: ColorProvider,
        accentColor: Color
    ) {
        val dateColor = if (item.isOverdue) ColorProvider(Color(0xFFD32F2F)) else ColorProvider(accentColor)
        Column(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.patientName}: ",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColorProvider
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = item.dueDate,
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = dateColor,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    private fun Divider(color: ColorProvider, modifier: GlanceModifier = GlanceModifier) {
        Box(
            modifier = modifier.fillMaxWidth().height(1.dp).background(color)
        ) {}
    }
}

/**
 * Minimal data source abstraction for widget due vaccination data.
 * Encapsulates WidgetDueDao access so the widget does not directly depend on Room/DAO.
 */
class WidgetLocalDataSource(
    private val widgetDueDao: WidgetDueDao
) {

    val dueItemsFlow: Flow<List<WidgetDueEntity>>
        get() = widgetDueDao.getDueItems()

    suspend fun getDueItemsFirst(): List<WidgetDueEntity> = widgetDueDao.getDueItems().first()
}