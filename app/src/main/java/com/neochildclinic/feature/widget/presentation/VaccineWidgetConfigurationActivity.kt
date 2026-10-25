package com.neochildclinic.feature.widget.presentation

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.neochildclinic.core.ui.SelectDropdown
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class VaccineWidgetConfigurationActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Read the widget's saved state BEFORE building the screen, so a reopened
        // configuration starts from the last-applied selections instead of hardcoded
        // defaults. Falls back to the same defaults when a key is absent.
        MainScope().launch {
            val glanceId = GlanceAppWidgetManager(this@VaccineWidgetConfigurationActivity)
                .getGlanceIdBy(appWidgetId)
            val prefs = getAppWidgetState(
                this@VaccineWidgetConfigurationActivity,
                PreferencesGlanceStateDefinition,
                glanceId
            )

            setContent {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        WidgetConfigurationScreen(
                            initialOpacity = prefs[VaccineWidget.OPACITY_KEY] ?: VaccineWidget.DEFAULT_OPACITY,
                            initialTheme = prefs[VaccineWidget.THEME_KEY] ?: VaccineWidgetTheme.SYSTEM.key,
                            initialFontSize = prefs[VaccineWidget.FONT_SIZE_KEY] ?: VaccineWidget.DEFAULT_FONT_SIZE,
                            initialPadding = prefs[VaccineWidget.PADDING_KEY] ?: VaccineWidget.DEFAULT_PADDING,
                            onConfirm = { opacity, theme, fontSize, padding ->
                                saveSettings(opacity, theme, fontSize, padding)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun saveSettings(opacity: Float, theme: String, fontSize: Float, padding: Float) {
        MainScope().launch {
            val glanceId = GlanceAppWidgetManager(this@VaccineWidgetConfigurationActivity)
                .getGlanceIdBy(appWidgetId)

            updateAppWidgetState(
                this@VaccineWidgetConfigurationActivity,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { prefs ->
                prefs.toMutablePreferences().apply {
                    set(VaccineWidget.OPACITY_KEY, opacity)
                    set(VaccineWidget.THEME_KEY, theme)
                    set(VaccineWidget.FONT_SIZE_KEY, fontSize)
                    set(VaccineWidget.PADDING_KEY, padding)
                }
            }

            VaccineWidget().update(this@VaccineWidgetConfigurationActivity, glanceId)

            setResult(RESULT_OK, Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            })
            finish()
        }
    }
}

@Composable
private fun WidgetConfigurationScreen(
    initialOpacity: Float,
    initialTheme: String,
    initialFontSize: Float,
    initialPadding: Float,
    onConfirm: (Float, String, Float, Float) -> Unit
) {
    var opacity by remember { mutableStateOf(initialOpacity) }
    var theme by remember { mutableStateOf(initialTheme) }
    var fontSize by remember { mutableStateOf(initialFontSize) }
    var padding by remember { mutableStateOf(initialPadding) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Edit Widget", style = MaterialTheme.typography.headlineSmall)

        Text(
            "Transparency: ${(opacity * 100).toInt()}%",
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = opacity,
            onValueChange = { opacity = it },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )

        // Reuses the app-wide exposed dropdown (core/ui/Dropdowns.kt) instead of the
        // former screen-popping stack of 9 FilterChips.
        SelectDropdown(
            items = VaccineWidgetTheme.entries.toList(),
            selected = VaccineWidgetTheme.fromKey(theme),
            onItemSelected = { theme = it.key },
            itemLabel = { it.label },
            label = "Theme"
        )

        Text(
            "Row font size: ${fontSize.roundToInt()} sp",
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = fontSize,
            onValueChange = { fontSize = it },
            valueRange = 10f..16f,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Padding: ${padding.roundToInt()} dp",
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = padding,
            onValueChange = { padding = it },
            valueRange = 0f..20f,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { onConfirm(opacity, theme, fontSize, padding) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Apply Settings")
        }
    }
}
