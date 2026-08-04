package com.neochildclinic.features.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToInventoryIssues: () -> Unit = {},
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val settingsState by viewModel.settings.collectAsState()
    val isBackfilling by viewModel.isBackfilling.collectAsState()
    val backfillResults by viewModel.backfillResults.collectAsState()
    
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var showBackfillConfirm by remember { mutableStateOf(false) }

    if (showBackfillConfirm) {
        AlertDialog(
            onDismissRequest = { showBackfillConfirm = false },
            title = { Text("Confirm Inventory Backfill") },
            text = { Text("This will deduct historical vaccine usage from current stock based on all past vaccination records. This cannot be undone. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        showBackfillConfirm = false
                        viewModel.runInventoryBackfill()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Confirm & Run") }
            },
            dismissButton = {
                TextButton(onClick = { showBackfillConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (backfillResults != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearBackfillResults() },
            title = { Text("Backfill Summary") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(backfillResults!!) { result ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.vaccineName, fontWeight = FontWeight.Bold)
                                Text(result.message, style = MaterialTheme.typography.bodySmall, color = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                            }
                            Text("Count: ${result.countFound}", style = MaterialTheme.typography.labelMedium)
                        }
                        HorizontalDivider(modifier = Modifier.alpha(0.5f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearBackfillResults() }) { Text("Close") }
            }
        )
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            settingsState?.let { settings ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Notifications Section
                    item {
                        ExpandableSettingsSection(
                            title = "Notifications",
                            icon = Icons.Default.Notifications,
                            isExpanded = expandedSection == "Notifications",
                            onExpandToggle = { expandedSection = if (expandedSection == "Notifications") null else "Notifications" }
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingSwitch(
                                    label = "Daily Summary Notification",
                                    supportingText = "Sends tasks overview at 08:00 AM",
                                    checked = settings.dailySummaryEnabled,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(dailySummaryEnabled = it)) }
                                )
                                SettingItem(label = "Summary Time", value = settings.reminderTime) {
                                    // Time picker could be added here
                                }
                            }
                        }
                    }

                    // Inventory Section
                    item {
                        ExpandableSettingsSection(
                            title = "Inventory",
                            icon = Icons.Default.Inventory,
                            isExpanded = expandedSection == "Inventory",
                            onExpandToggle = { expandedSection = if (expandedSection == "Inventory") null else "Inventory" }
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingSwitch(
                                    label = "Low Stock Alerts",
                                    supportingText = "Notify when vaccine stock is low",
                                    checked = settings.lowStockEnabled,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(lowStockEnabled = it)) }
                                )
                                SettingSlider(
                                    label = "Low Stock Threshold",
                                    value = settings.lowStockThreshold.toFloat(),
                                    range = 1f..20f,
                                    steps = 19,
                                    onValueChange = { viewModel.updateSettings(settings.copy(lowStockThreshold = it.toInt())) }
                                )
                            }
                        }
                    }

                    // Backup Section
                    item {
                        ExpandableSettingsSection(
                            title = "Backup",
                            icon = Icons.Default.Backup,
                            isExpanded = expandedSection == "Backup",
                            onExpandToggle = { expandedSection = if (expandedSection == "Backup") null else "Backup" }
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingSwitch(
                                    label = "Sync & Backup Alerts",
                                    supportingText = "Notify on synchronization failures",
                                    checked = settings.syncAlertsEnabled,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(syncAlertsEnabled = it)) }
                                )
                                Button(
                                    onClick = { /* Could trigger manual export */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Manual Cloud Backup")
                                }
                            }
                        }
                    }

                    // Security Section
                    item {
                        ExpandableSettingsSection(
                            title = "Security",
                            icon = Icons.Default.Security,
                            isExpanded = expandedSection == "Security",
                            onExpandToggle = { expandedSection = if (expandedSection == "Security") null else "Security" }
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingSwitch(
                                    label = "Biometric Lock",
                                    supportingText = "Enable fingerprint/face ID",
                                    checked = settings.biometricLockEnabled,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(biometricLockEnabled = it)) }
                                )
                                SettingSwitch(
                                    label = "Always Authenticate",
                                    supportingText = "Auth on every app open",
                                    checked = settings.authOnEveryOpen,
                                    onCheckedChange = { viewModel.updateSettings(settings.copy(authOnEveryOpen = it)) }
                                )
                                SettingSlider(
                                    label = "Inactivity Days",
                                    value = settings.inactivityDaysThreshold.toFloat(),
                                    range = 1f..30f,
                                    steps = 29,
                                    onValueChange = { viewModel.updateSettings(settings.copy(inactivityDaysThreshold = it.toInt())) }
                                )
                            }
                        }
                    }

                    // Admin Maintenance Section
                    if (viewModel.isAdmin) {
                        item {
                            ExpandableSettingsSection(
                                title = "Maintenance (Admin)",
                                icon = Icons.Default.Build,
                                isExpanded = expandedSection == "Maintenance",
                                onExpandToggle = { expandedSection = if (expandedSection == "Maintenance") null else "Maintenance" }
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "Perform system-wide data reconciliation and cleanup tasks.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Button(
                                        onClick = { showBackfillConfirm = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isBackfilling,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                    ) {
                                        if (isBackfilling) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.error)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Processing Backfill...")
                                        } else {
                                            Icon(Icons.Default.History, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Backfill Inventory From History")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = onNavigateToInventoryIssues,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Manage Inventory Issues")
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun ExpandableSettingsSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingSwitch(label: String, supportingText: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingItem(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value.toInt().toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
