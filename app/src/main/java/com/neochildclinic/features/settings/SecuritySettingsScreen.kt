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
fun SecuritySettingsScreen(onBack: () -> Unit, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    AppBackground {
        Scaffold(topBar = { SettingsDetailTopBar("Security", onBack) }) { padding ->
            settings?.let { s ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SettingSwitch("Biometric Lock", "Enable fingerprint/face ID", s.biometricLockEnabled) {
                                    viewModel.updateSettings(s.copy(biometricLockEnabled = it))
                                }
                                SettingSwitch("Always Authenticate", "Auth on every app open", s.authOnEveryOpen) {
                                    viewModel.updateSettings(s.copy(authOnEveryOpen = it))
                                }
                                SettingSlider("Inactivity Days", s.inactivityDaysThreshold.toFloat(), 1f..30f, 29) {
                                    viewModel.updateSettings(s.copy(inactivityDaysThreshold = it.toInt()))
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
