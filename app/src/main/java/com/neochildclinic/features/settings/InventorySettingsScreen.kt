package com.neochildclinic.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground


@Composable
fun InventorySettingsScreen(onBack: () -> Unit, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    AppBackground {
        Scaffold(topBar = { SettingsDetailTopBar("Inventory", onBack) }) { padding ->
            settings?.let { s ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SettingSwitch(
                                    "Low Stock Alerts",
                                    "Notify when vaccine stock is low",
                                    s.lowStockEnabled
                                ) { viewModel.updateSettings(s.copy(lowStockEnabled = it)) }
                                SettingSlider(
                                    "Low Stock Threshold",
                                    s.lowStockThreshold.toFloat(),
                                    1f..20f,
                                    19
                                ) { viewModel.updateSettings(s.copy(lowStockThreshold = it.toInt())) }
                            }
                        }
                    }
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
