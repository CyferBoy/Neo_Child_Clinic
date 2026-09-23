package com.neochildclinic.features.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.entity.BackupHistoryEntity
import com.neochildclinic.domain.model.*
import java.text.NumberFormat

@Composable
fun BackupSettingsScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    var pendingAction by remember { mutableStateOf<PendingPasswordAction?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) pendingAction = PendingPasswordAction.ExportLocal(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingAction = PendingPasswordAction.ImportLocal(uri)
    }

    AppBackground {
        Scaffold(topBar = { SettingsDetailTopBar("Backup & Restore", onBack) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsSection("Local Backup") {
                        SettingsRow(Icons.Default.FileDownload, "Export Backup") {
                            exportLauncher.launch(defaultBackupFileName())
                        }
                        SettingsDivider()
                        SettingsRow(Icons.Default.FileUpload, "Import Backup") {
                            importLauncher.launch(arrayOf("*/*"))
                        }
                    }
                }

                if (state.hasSafetyBackup) {
                    item {
                        SettingsSection("Undo") {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "A safety copy of your data from just before the last restore is available.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedButton(onClick = { viewModel.restoreSafetyBackup() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Undo Last Restore")
                                }
                            }
                        }
                    }
                }

                item {
                    CloudBackupSection(
                        state = state,
                        onBackupNow = { pendingAction = PendingPasswordAction.CloudBackup },
                        onRestore = { id -> pendingAction = PendingPasswordAction.CloudRestore(id) },
                        onDelete = { id -> viewModel.deleteCloudBackup(id) }
                    )
                }

                item {
                    AutomaticBackupSection(
                        settings = state.autoBackupSettings,
                        cloudConfigured = state.cloudConfigured,
                        onEnable = { pendingAction = PendingPasswordAction.EnableAuto },
                        onDisable = { viewModel.disableAutomaticBackup() },
                        onUpdate = { viewModel.updateAutomaticBackupSettings(it) }
                    )
                }

                if (state.history.isNotEmpty()) {
                    item {
                        SettingsSection("Backup History") {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                state.history.take(20).forEach { entry ->
                                    HistoryRow(entry)
                                    SettingsDivider()
                                }
                            }
                        }
                    }
                }
            }

            if (state.isBusy) {
                BackupProgressDialog(state.progressLabel ?: "Working...")
            }

            state.pendingRestoreSummary?.let { summary ->
                RestoreSummaryDialog(
                    summary = summary,
                    onDismiss = { viewModel.dismissRestoreSummary() },
                    onReplace = { viewModel.confirmRestore(RestoreMode.REPLACE) },
                    onMerge = { viewModel.confirmRestore(RestoreMode.MERGE) }
                )
            }

            state.message?.let { msg ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissMessage() },
                    confirmButton = { TextButton(onClick = { viewModel.dismissMessage() }) { Text("OK") } },
                    title = { Text(if (msg.isError) "Backup" else "Success") },
                    text = { Text(msg.text) }
                )
            }

            pendingAction?.let { action ->
                PasswordPromptDialog(
                    action = action,
                    onDismiss = { pendingAction = null },
                    onConfirm = { password, settings ->
                        when (action) {
                            is PendingPasswordAction.ExportLocal -> viewModel.exportBackup(action.uri, password)
                            is PendingPasswordAction.ImportLocal -> viewModel.peekLocalBackup(action.uri, password)
                            PendingPasswordAction.CloudBackup -> viewModel.cloudBackupNow(password)
                            is PendingPasswordAction.CloudRestore -> viewModel.peekCloudBackup(action.backupId, password)
                            PendingPasswordAction.EnableAuto -> viewModel.enableAutomaticBackup(
                                password, settings ?: AutoBackupSettings(enabled = true)
                            )
                        }
                        pendingAction = null
                    }
                )
            }
        }
    }
}

private fun defaultBackupFileName(): String {
    val ts = PatientUtils.getCurrentIsoTimestamp().replace(":", "").replace("-", "").take(13).replace("T", "_")
    return "NeoChildClinic_Backup_$ts.nccb"
}

private sealed class PendingPasswordAction {
    data class ExportLocal(val uri: Uri) : PendingPasswordAction()
    data class ImportLocal(val uri: Uri) : PendingPasswordAction()
    data object CloudBackup : PendingPasswordAction()
    data class CloudRestore(val backupId: String) : PendingPasswordAction()
    data object EnableAuto : PendingPasswordAction()
}

@Composable
private fun CloudBackupSection(
    state: BackupUiState,
    onBackupNow: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    SettingsSection("Cloud Backup") {
        Column(Modifier.padding(bottom = 4.dp)) {
            if (!state.cloudConfigured) {
                Text(
                    "Cloud backup is not configured for this build.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SettingsRow(Icons.Default.CloudUpload, "Backup Now", onBackupNow)
                SettingsDivider()
                if (state.cloudBackupsLoading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (state.cloudBackups.isEmpty()) {
                    Text(
                        "No cloud backups yet.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.cloudBackups.forEach { backup ->
                        CloudBackupRow(backup, onRestore = { onRestore(backup.backupId) }, onDelete = { onDelete(backup.backupId) })
                        SettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudBackupRow(backup: CloudBackupMetadata, onRestore: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(PatientUtils.formatDateTimeForDisplay(backup.createdAt), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${PatientUtils.formatBytes(backup.sizeBytes)} \u2022 v${backup.appVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRestore) { Text("Restore") }
        Box {
            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun AutomaticBackupSection(
    settings: AutoBackupSettings,
    cloudConfigured: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onUpdate: (AutoBackupSettings) -> Unit
) {
    SettingsSection("Automatic Backup") {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingSwitch(
                "Automatic Backup",
                if (settings.enabled) "Runs automatically on this device" else "Off",
                checked = settings.enabled
            ) { checked -> if (checked) onEnable() else onDisable() }

            if (settings.enabled) {
                Text("Frequency", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmented(
                    options = listOf("Daily" to BackupFrequency.DAILY, "Weekly" to BackupFrequency.WEEKLY),
                    selected = settings.frequency,
                    onSelect = { onUpdate(settings.copy(frequency = it)) }
                )

                SettingSwitch(
                    "Include Cloud Backup",
                    if (cloudConfigured) "Also uploads to cloud storage" else "Cloud backup not configured for this build",
                    checked = settings.cloudEnabled,
                    enabled = cloudConfigured
                ) { onUpdate(settings.copy(cloudEnabled = it)) }

                if (settings.cloudEnabled) {
                    RetentionStepper(
                        value = settings.retainCount,
                        onChange = { onUpdate(settings.copy(retainCount = it)) }
                    )
                }

                if (settings.lastRunAt != null) {
                    Text(
                        "Last run: ${PatientUtils.formatDateTimeForDisplay(settings.lastRunAt)} (${settings.lastRunStatus ?: "unknown"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SingleChoiceSegmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun RetentionStepper(value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Keep last cloud backups", style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > 1) onChange(value - 1) }) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
            Text(value.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { if (value < 30) onChange(value + 1) }) { Icon(Icons.Default.Add, contentDescription = "Increase") }
        }
    }
}

@Composable
private fun HistoryRow(entry: BackupHistoryEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(historyTypeLabel(entry.type, entry.location), style = MaterialTheme.typography.bodyLarge)
            Text(
                PatientUtils.formatDateTimeForDisplay(entry.createdAt) + if (entry.sizeBytes > 0) " \u2022 ${PatientUtils.formatBytes(entry.sizeBytes)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(entry.status) },
            colors = AssistChipDefaults.assistChipColors(
                disabledLabelColor = if (entry.status == "SUCCESS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        )
    }
}

private fun historyTypeLabel(type: String, location: String): String = when (type) {
    "LOCAL_EXPORT" -> if (location == "CLOUD") "Cloud Backup" else "Local Export"
    "LOCAL_IMPORT" -> "Local Restore"
    "CLOUD_BACKUP" -> "Cloud Backup"
    "CLOUD_RESTORE" -> "Cloud Restore"
    "SAFETY_BACKUP" -> "Undo Restore"
    else -> type
}

@Composable
private fun BackupProgressDialog(label: String) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 4.dp) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun RestoreSummaryDialog(summary: RestoreSummary, onDismiss: () -> Unit, onReplace: () -> Unit, onMerge: () -> Unit) {
    var showReplaceConfirm by remember { mutableStateOf(false) }
    val nf = remember { NumberFormat.getIntegerInstance() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Created: ${PatientUtils.formatDateTimeForDisplay(summary.createdAt)}", style = MaterialTheme.typography.bodyMedium)
                Text("App version: ${summary.appVersion}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                summary.recordCounts.entries.filter { it.value > 0 }.forEach { (label, count) ->
                    Text("\u2022 ${nf.format(count)} $label", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showReplaceConfirm = true }) { Text("Replace") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onMerge) { Text("Merge") }
            }
        }
    )

    if (showReplaceConfirm) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            title = { Text("Replace current data?") },
            text = { Text("This will replace the current clinic data with the selected backup. A safety backup will be created before continuing.") },
            confirmButton = {
                TextButton(onClick = { showReplaceConfirm = false; onReplace() }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PasswordPromptDialog(
    action: PendingPasswordAction,
    onDismiss: () -> Unit,
    onConfirm: (password: CharArray, autoSettings: AutoBackupSettings?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val needsConfirm = action is PendingPasswordAction.ExportLocal || action is PendingPasswordAction.CloudBackup || action is PendingPasswordAction.EnableAuto
    var frequency by remember { mutableStateOf(BackupFrequency.DAILY) }
    var cloudEnabled by remember { mutableStateOf(false) }

    val title = when (action) {
        is PendingPasswordAction.ExportLocal -> "Set Backup Password"
        is PendingPasswordAction.ImportLocal -> "Enter Backup Password"
        PendingPasswordAction.CloudBackup -> "Set Backup Password"
        is PendingPasswordAction.CloudRestore -> "Enter Backup Password"
        PendingPasswordAction.EnableAuto -> "Set Automatic Backup Password"
    }
    val isValid = password.length >= 8 && (!needsConfirm || password == confirmPassword)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (needsConfirm) {
                    Text(
                        "This password encrypts your backup. Neo Child Clinic never stores it - if you forget it, the backup cannot be recovered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (needsConfirm) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (action is PendingPasswordAction.EnableAuto) {
                    Text("Frequency", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmented(
                        options = listOf("Daily" to BackupFrequency.DAILY, "Weekly" to BackupFrequency.WEEKLY),
                        selected = frequency,
                        onSelect = { frequency = it }
                    )
                    SettingSwitch("Include Cloud Backup", null, checked = cloudEnabled) { cloudEnabled = it }
                }
                if (password.isNotEmpty() && password.length < 8) {
                    Text("Password must be at least 8 characters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val settings = if (action is PendingPasswordAction.EnableAuto)
                        AutoBackupSettings(enabled = true, frequency = frequency, cloudEnabled = cloudEnabled)
                    else null
                    // Convert to CharArray at this boundary - everything downstream
                    // (ViewModel/Repository/BackupCrypto) works with CharArray and
                    // actively clears it once done. Compose's TextField is fundamentally
                    // String-backed, so `password`/`confirmPassword` themselves can't be
                    // zeroed - only dropped for GC, which clearing them to "" below does
                    // as soon as this dialog closes.
                    val passwordChars = password.toCharArray()
                    password = ""
                    confirmPassword = ""
                    onConfirm(passwordChars, settings)
                }
            ) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
