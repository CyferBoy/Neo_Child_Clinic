package com.neochildclinic.feature.widget.presentation

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
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.unit.ColorProvider
import com.neochildclinic.R
import com.neochildclinic.app.MainActivity
import com.neochildclinic.feature.widget.data.WidgetLocalDataSource
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.neochildclinic.data.local.entity.WidgetDueEntity

class VaccineWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        val OPACITY_KEY = floatPreferencesKey("widget_opacity")
        val THEME_KEY = stringPreferencesKey("widget_theme")
        val FONT_SIZE_KEY = floatPreferencesKey("widget_font_size")
        val PADDING_KEY = floatPreferencesKey("widget_padding")
        const val DEFAULT_OPACITY = 0.85f
        const val DEFAULT_FONT_SIZE = 12f
        const val DEFAULT_PADDING = 6f

        // Design baselines the configuration defaults map to: font sizes scale by
        // ratio to DESIGN_ROW_SP and internal gaps by ratio to DEFAULT_PADDING, so
        // the size/padding sliders introduce no new hardcoded values. At the defaults
        // the widget renders exactly as before the sliders existed.
        private const val DESIGN_TITLE_SP = 16f
        private const val DESIGN_EMPTY_SP = 15f
        private const val DESIGN_ROW_SP = 12f
        private const val DESIGN_GAP_DP = 4f
        private const val DESIGN_ROW_GAP_DP = 3f
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val dueItems = entryPoint.widgetLocalDataSource().getDueItemsFirst()

        provideContent {
            val prefs = currentState<Preferences>()
            val opacity = prefs[OPACITY_KEY] ?: DEFAULT_OPACITY
            val theme = VaccineWidgetTheme.fromKey(prefs[THEME_KEY])
            val fontScale = (prefs[FONT_SIZE_KEY] ?: DEFAULT_FONT_SIZE) / DESIGN_ROW_SP
            val padding = prefs[PADDING_KEY] ?: DEFAULT_PADDING
            WidgetContent(context, dueItems, opacity, theme, fontScale, padding)
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        items: List<WidgetDueEntity>,
        opacity: Float,
        theme: VaccineWidgetTheme,
        fontScale: Float,
        padding: Float
    ) {
        val colors = theme.colors(context)
        val resolvedBgColor = colors.background.copy(alpha = opacity)
        val textColorProvider = androidx.glance.color.ColorProvider(colors.primaryText, colors.primaryText)
        val secondaryTextProvider = androidx.glance.color.ColorProvider(colors.secondaryText, colors.secondaryText)
        val padScale = padding / DEFAULT_PADDING

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(resolvedBgColor)
                .cornerRadius(24.dp)
                .padding(padding.dp)
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
                        fontSize = (DESIGN_TITLE_SP * fontScale).sp,
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
                            style = TextStyle(fontSize = (DESIGN_EMPTY_SP * fontScale).sp, color = secondaryTextProvider)
                        )
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(top = (DESIGN_GAP_DP * padScale).dp)) {
                        items(items) { item ->
                            VaccineRow(item, textColorProvider, colors.accent, fontScale, padScale)
                        }
                    }
                }
            }

            // Footer: Centered Refresh Button
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = (DESIGN_GAP_DP * padScale).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Refresh",
                    colorFilter = ColorFilter.tint(textColorProvider),
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
        accentColor: Color,
        fontScale: Float,
        padScale: Float
    ) {
        val dateColor = if (item.isOverdue) androidx.glance.color.ColorProvider(Color(0xFFD32F2F), Color(0xFFD32F2F)) else androidx.glance.color.ColorProvider(accentColor, accentColor)
        Column(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = (DESIGN_ROW_GAP_DP * padScale).dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.patientName}: ",
                    style = TextStyle(
                        fontSize = (DESIGN_ROW_SP * fontScale).sp,
                        fontWeight = FontWeight.Medium,
                        color = textColorProvider
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = item.dueDate,
                    style = TextStyle(
                        fontSize = (DESIGN_ROW_SP * fontScale).sp,
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetLocalDataSource(): WidgetLocalDataSource
}
