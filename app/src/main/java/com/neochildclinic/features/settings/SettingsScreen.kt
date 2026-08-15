package com.neochildclinic.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNotifications: () -> Unit,
    onInventory: () -> Unit,
    onBackup: () -> Unit,
    onSecurity: () -> Unit,
    onHelpSupport: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTermsOfService: () -> Unit,
    onCheckForUpdates: () -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SettingsSection("App") {
                        SettingsRow(Icons.Default.Notifications, "Notifications", onNotifications)
                        SettingsDivider()
                        SettingsRow(Icons.Default.Inventory, "Inventory", onInventory)
                        SettingsDivider()
                        SettingsRow(Icons.Default.Backup, "Backup", onBackup)
                    }
                }
                item {
                    SettingsSection("Security") {
                        SettingsRow(Icons.Default.Security, "Security", onSecurity)
                    }
                }
                item {
                    SettingsSection("Help & About") {
                        SettingsRow(Icons.Default.HeadsetMic, "Help & Support", onHelpSupport)
                        SettingsDivider()
                        SettingsRow(Icons.Default.Lock, "Privacy Policy", onPrivacyPolicy)
                        SettingsDivider()
                        SettingsRow(Icons.Default.Description, "Terms of Service", onTermsOfService)
                        SettingsDivider()
                        SettingsRow(Icons.Default.CloudUpload, "Check for Updates", onCheckForUpdates)
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Version: ${com.neochildclinic.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "© 2026 Neo Child Clinic. All rights reserved.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
