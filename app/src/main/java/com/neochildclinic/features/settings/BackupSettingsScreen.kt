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
fun BackupSettingsScreen(onBack: () -> Unit, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    AppBackground {
        Scaffold(topBar = { SettingsDetailTopBar("Backup", onBack) }) { padding ->
            settings?.let { s ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SettingSwitch(
                                    "Sync & Backup Alerts",
                                    "Notify on synchronization failures",
                                    s.syncAlertsEnabled
                                ) { viewModel.updateSettings(s.copy(syncAlertsEnabled = it)) }
                                Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Manual Cloud Backup")
                                }
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
