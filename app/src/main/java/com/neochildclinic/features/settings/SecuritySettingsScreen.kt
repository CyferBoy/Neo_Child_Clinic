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
import com.neochildclinic.core.utils.BiometricAuthenticator
import com.neochildclinic.core.utils.BiometricLockManager
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity


@Composable
fun SecuritySettingsScreen(onBack: () -> Unit, viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    
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
                                SettingSwitch("Biometric Lock", "Enable fingerprint/face ID", s.biometricLockEnabled) { requested ->
                                    if (requested) {
                                        BiometricLockManager.setProtectionEnabled(true)
                                        viewModel.updateSettings(s.copy(biometricLockEnabled = true))
                                    } else if (activity != null) {
                                        BiometricAuthenticator.authenticate(
                                            activity = activity,
                                            title = "Disable Biometric Lock",
                                            subtitle = "Authenticate to change security settings"
                                        ) {
                                            BiometricLockManager.setProtectionEnabled(false)
                                             viewModel.updateSettings(s.copy(biometricLockEnabled = false))
                                        }
                                    }
                                }
                                SettingSwitch(
                                    label = "Always Authenticate",
                                    supportingText = "Auth on every app open",
                                    checked = s.authOnEveryOpen,
                                    enabled = s.biometricLockEnabled
                                ) { requested ->
                                    if (!s.authOnEveryOpen || activity == null) {
                                        viewModel.updateSettings(s.copy(authOnEveryOpen = requested))
                                    } else if (!requested) {
                                        BiometricAuthenticator.authenticate(
                                            activity = activity,
                                            title = "Change Authentication Setting",
                                            subtitle = "Authenticate to reduce app security"
                                        ) {
                                            viewModel.updateSettings(s.copy(authOnEveryOpen = false))
                                        }
                                    }
                                }
                                SettingSlider(
                                    label = "Inactivity Days",
                                    value = s.inactivityDaysThreshold.toFloat(),
                                    range = 1f..30f,
                                    steps = 29,
                                    enabled = s.biometricLockEnabled
                                ) { requested ->
                                    if (requested.toInt() <= s.inactivityDaysThreshold || activity == null) {
                                        viewModel.updateSettings(s.copy(inactivityDaysThreshold = requested.toInt()))
                                    } else {
                                        BiometricAuthenticator.authenticate(
                                            activity = activity,
                                            title = "Change Security Setting",
                                            subtitle = "Authenticate to increase inactivity timeout"
                                        ) {
                                            viewModel.updateSettings(s.copy(inactivityDaysThreshold = requested.toInt()))
                                        }
                                    }
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
