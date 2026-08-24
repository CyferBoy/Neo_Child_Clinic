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
import com.neochildclinic.app.MainActivity
import com.neochildclinic.data.local.database.AppDatabase
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
        val dueItems = database.widgetDueDao().getDueItems().first()

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
                .padding(9.dp)
                .clickable(
                    actionStartActivity<MainActivity>(
                        actionParametersOf(
                            ActionParameters.Key<Boolean>("OPEN_DUE_TAB") to true
                        )
                    )
                )
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = "Due Vaccination",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColorProvider
                    )
                )
                Divider(secondaryTextProvider)
            }

            if (items.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No upcoming vaccinations",
                        style = TextStyle(fontSize = 14.sp, color = secondaryTextProvider)
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColorProvider
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(
                    text = item.dueDate,
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = dateColor,
                        fontWeight = if (item.isOverdue) FontWeight.Bold else FontWeight.Medium
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

enum class VaccineWidgetTheme(
    val key: String,
    val label: String,
    private val lightBackground: Color,
    private val darkBackground: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color
) {
    SYSTEM("system", "System", Color(0xFFF7F7F7), Color(0xFF202124), Color.Unspecified, Color.Unspecified, Color(0xFF1976D2)),
    LIGHT("light", "Light", Color.White, Color.White, Color(0xFF1C1B1F), Color(0xFF5F6368), Color(0xFF1976D2)),
    DARK("dark", "Dark", Color(0xFF303030), Color(0xFF303030), Color.White, Color(0xFFCAC4D0), Color(0xFF90CAF9)),
    MIDNIGHT("midnight", "Midnight", Color(0xFF050608), Color(0xFF050608), Color.White, Color(0xFFB9C1D0), Color(0xFF64B5F6)),
    GLASS("glass", "Glass", Color(0x99202020), Color(0x99202020), Color.White, Color(0xFFE0E0E0), Color(0xFF90CAF9)),
    CLINIC_BLUE("clinic_blue", "Clinic Blue", Color(0xFF1565C0), Color(0xFF0D47A1), Color.White, Color(0xFFD6E8FF), Color(0xFF90CAF9)),
    CLINIC_GREEN("clinic_green", "Clinic Green", Color(0xFF2E7D32), Color(0xFF1B5E20), Color.White, Color(0xFFD7F2D8), Color(0xFFA5D6A7)),
    WARM("warm", "Warm", Color(0xFFFFF3E0), Color(0xFF4E342E), Color(0xFF3E2723), Color(0xFF6D4C41), Color(0xFFEF6C00)),
    HIGH_CONTRAST("high_contrast", "High Contrast", Color.Black, Color.Black, Color.White, Color.White, Color(0xFFFFFF00));

    data class Palette(
        val background: Color,
        val primaryText: Color,
        val secondaryText: Color,
        val accent: Color
    )

    fun colors(context: Context): Palette {
        if (this == SYSTEM) {
            val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            return if (night) {
                Palette(Color(0xFF202124), Color.White, Color(0xFFCAC4D0), Color(0xFF90CAF9))
            } else {
                Palette(Color(0xFFF7F7F7), Color(0xFF1C1B1F), Color(0xFF5F6368), Color(0xFF1976D2))
            }
        }
        return Palette(lightBackground, primaryText, secondaryText, accent)
    }

    companion object {
        fun fromKey(key: String?): VaccineWidgetTheme =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
