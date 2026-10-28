package com.neochildclinic.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar

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
    onCheckForUpdates: () -> Unit,
    viewModel: SettingsViewModel
) {
    val themeMode by viewModel.themeMode.collectAsState()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("Settings") },
                    onBack = onBack,
                    colors = TopAppBarDefaults.topAppBarColors()
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.screen),
                contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // 1. Theme Section
                item {
                    SettingsSection(title = "Appearance") {
                        ThemeOption("Light", "Clean warm clinical ivory", themeMode == "light") { viewModel.setThemeMode("light") }
                        SettingsDivider()
                        ThemeOption("Dark", "Forest ink clinical theme", themeMode == "dark") { viewModel.setThemeMode("dark") }
                        SettingsDivider()
                        ThemeOption("System Default", "Follow device appearance", themeMode == "system") { viewModel.setThemeMode("system") }
                    }
                }

                // 2. Application Preferences
                item {
                    SettingsSection(title = "Preferences") {
                        SettingsRow(
                            icon = Icons.Default.Notifications,
                            title = "Notifications",
                            subtitle = "Vaccination, stock and summary alerts",
                            onClick = onNotifications
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.Inventory,
                            title = "Inventory",
                            subtitle = "Expiry thresholds and low-stock alerts",
                            onClick = onInventory
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.Security,
                            title = "Security",
                            subtitle = "Biometric lock and app authentication",
                            onClick = onSecurity
                        )
                    }
                }

                // 3. Backup & Data
                item {
                    SettingsSection(title = "Data Management") {
                        SettingsRow(
                            icon = Icons.Default.Backup,
                            title = "Backup & Restore",
                            subtitle = "Local snapshots and cloud archive",
                            onClick = onBackup
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.CloudUpload,
                            title = "Check for Updates",
                            subtitle = "Current version v${com.neochildclinic.BuildConfig.VERSION_NAME}",
                            onClick = onCheckForUpdates
                        )
                    }
                }

                // 4. Help & About
                item {
                    SettingsSection(title = "Support & Legal") {
                        SettingsRow(
                            icon = Icons.Default.HeadsetMic,
                            title = "Help & Support",
                            subtitle = "FAQs, clinical guides and contact",
                            onClick = onHelpSupport
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.Lock,
                            title = "Privacy Policy",
                            subtitle = "Patient data confidentiality",
                            onClick = onPrivacyPolicy
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Default.Description,
                            title = "Terms of Service",
                            subtitle = "Usage rules and clinic agreements",
                            onClick = onTermsOfService
                        )
                    }
                }

                // 5. Version & Copyright Footer
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Neo Child Clinic v${com.neochildclinic.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Protected by Warm Clinical System · Offline-first",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Spacing.card, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}
