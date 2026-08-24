package com.neochildclinic.features.widget

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
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WidgetConfigurationScreen(
                        onConfirm = { opacity, theme -> saveSettings(opacity, theme) }
                    )
                }
            }
        }
    }

    private fun saveSettings(opacity: Float, theme: String) {
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
private fun WidgetConfigurationScreen(onConfirm: (Float, String) -> Unit) {
    var opacity by remember { mutableStateOf(0.85f) }
    var theme by remember { mutableStateOf(VaccineWidgetTheme.SYSTEM.key) }

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

        Text(
            "Theme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        VaccineWidgetTheme.entries.forEach { option ->
            FilterChip(
                selected = theme == option.key,
                onClick = { theme = option.key },
                label = { Text(option.label) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = { onConfirm(opacity, theme) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text("Apply Settings")
        }
    }
}
