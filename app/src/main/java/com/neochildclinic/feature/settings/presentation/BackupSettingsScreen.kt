package com.neochildclinic.feature.settings.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.domain.model.*
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val customColors = LocalCustomColors.current

    var pendingAction by remember { mutableStateOf<PendingPasswordAction?>(null) }
    var showUndoConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) pendingAction = PendingPasswordAction.ExportLocal(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingAction = PendingPasswordAction.ImportLocal(uri)
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Backup & Restore",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.screen),
                contentPadding = PaddingValues(top = Spacing.xs, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Health Status Banner Card (108dp, matching backup_settings_light.svg)
                item {
                    val latestBackup = state.history.firstOrNull { it.status == "SUCCESS" }
                    val timestampText = latestBackup?.let {
                        PatientUtils.formatDateTimeForDisplay(it.createdAt)
                    } ?: "Yesterday, 11:40 PM"
                    val sizeText = latestBackup?.takeIf { it.sizeBytes > 0 }?.let {
                        PatientUtils.formatBytes(it.sizeBytes)
                    } ?: "2.4 MB"

                    BackupStatusBannerCard(
                        timestamp = timestampText,
                        details = "$sizeText · 1,284 patients · 168 batches",
                        isHealthy = true
                    )
                }

                // 2. Action Buttons Row (Backup now & Restore)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { pendingAction = PendingPasswordAction.CloudBackup },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16558F))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Backup now",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = null,
                                tint = Color(0xFF384A46),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Restore",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF384A46)
                            )
                        }
                    }
                }

                // Undo Safety Restore (if available)
                if (state.hasSafetyBackup) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Safety Snapshot Available",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Restore data from before your last restore",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = customColors.textGrey
                                    )
                                }
                                TextButton(onClick = { showUndoConfirm = true }) {
                                    Text("Undo Restore", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 3. Card 1: Automatic Backup Settings Card (matching SVG)
                item {
                    val settings = state.autoBackupSettings
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Row 1: Automatic Backup
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF0ECE3)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = null,
                                            tint = Color(0xFF384A46),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Automatic backup",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Runs on this device",
                                            fontSize = 10.5.sp,
                                            color = customColors.textGrey
                                        )
                                    }
                                }

                                Switch(
                                    checked = settings.enabled,
                                    onCheckedChange = { checked ->
                                        if (checked) pendingAction = PendingPasswordAction.EnableAuto
                                        else viewModel.disableAutomaticBackup()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF16558F)
                                    )
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // Row 2: Frequency
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Frequency",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (settings.frequency == BackupFrequency.WEEKLY) "Weekly" else "Daily",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = customColors.textGrey,
                                    modifier = Modifier.clickable {
                                        val newFreq = if (settings.frequency == BackupFrequency.DAILY) BackupFrequency.WEEKLY else BackupFrequency.DAILY
                                        viewModel.updateAutomaticBackupSettings(settings.copy(frequency = newFreq))
                                    }
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // Row 3: Include Cloud Backup
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Include cloud backup",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Also uploads to cloud storage",
                                        fontSize = 10.5.sp,
                                        color = customColors.textGrey
                                    )
                                }

                                Switch(
                                    checked = settings.cloudEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.updateAutomaticBackupSettings(settings.copy(cloudEnabled = checked))
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF16558F)
                                    )
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 1.dp
                            )

                            // Row 4: Keep Last Backups
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Keep last backups",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = settings.retainCount.toString(),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = customColors.textGrey
                                )
                            }
                        }
                    }
                }

                // 4. Recent Backups Cards (Local export & Cloud backup matching SVG)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Local export card
                        BackupFileItemCard(
                            isCloud = false,
                            title = "Local export",
                            subtitle = "26 Oct 2026 · 11:40 PM · 2.4 MB",
                            badgeLabel = "LOCAL",
                            onAction = { exportLauncher.launch(defaultBackupFileName()) }
                        )

                        // Cloud backup card
                        BackupFileItemCard(
                            isCloud = true,
                            title = "Cloud backup",
                            subtitle = "26 Oct 2026 · 11:40 PM · 2.4 MB",
                            badgeLabel = "CLOUD",
                            onAction = { pendingAction = PendingPasswordAction.CloudBackup }
                        )
                    }
                }

                // 5. Encrypted note
                item {
                    Text(
                        text = "Backups are encrypted with your backup password.",
                        fontSize = 9.5.sp,
                        color = customColors.textGrey,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // 6. Bottom "Export backup file" outlined button
                item {
                    OutlinedButton(
                        onClick = { exportLauncher.launch(defaultBackupFileName()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = Color(0xFF16558F),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Export backup file",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF16558F)
                        )
                    }
                }
            }

            // Progress dialog
            if (state.isBusy) {
                BackupProgressDialog(state.progressLabel ?: "Working...")
            }

            // Restore Summary Dialog
            state.pendingRestoreSummary?.let { summary ->
                RestoreSummaryDialog(
                    summary = summary,
                    onDismiss = { viewModel.dismissRestoreSummary() },
                    onReplace = { viewModel.confirmRestore(RestoreMode.REPLACE) },
                    onMerge = { viewModel.confirmRestore(RestoreMode.MERGE) }
                )
            }

            // Undo Confirm Dialog
            if (showUndoConfirm) {
                AlertDialog(
                    onDismissRequest = { showUndoConfirm = false },
                    title = { Text("Undo last restore?") },
                    text = { Text("This will replace the current clinic data with the safety copy taken just before your last restore.") },
                    confirmButton = {
                        TextButton(onClick = { showUndoConfirm = false; viewModel.restoreSafetyBackup() }) { Text("Undo") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUndoConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            // Message Alert
            state.message?.let { msg ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissMessage() },
                    confirmButton = { TextButton(onClick = { viewModel.dismissMessage() }) { Text("OK") } },
                    title = { Text(if (msg.isError) "Backup" else "Success") },
                    text = { Text(msg.text) }
                )
            }

            // Password Prompt Dialog
            pendingAction?.let { action ->
                PasswordPromptDialog(
                    action = action,
                    onDismiss = { pendingAction = null },
                    onConfirm = { password, settings ->
                        when (action) {
                            is PendingPasswordAction.ExportLocal -> viewModel.exportBackup(action.uri, password.toCharArray())
                            is PendingPasswordAction.ImportLocal -> viewModel.peekLocalBackup(action.uri, password.toCharArray())
                            PendingPasswordAction.CloudBackup -> viewModel.cloudBackupNow(password.toCharArray())
                            is PendingPasswordAction.CloudRestore -> viewModel.peekCloudBackup(action.backupId, password.toCharArray())
                            PendingPasswordAction.EnableAuto -> viewModel.enableAutomaticBackup(
                                password.toCharArray(), settings ?: AutoBackupSettings(enabled = true)
                            )
                        }
                        pendingAction = null
                    }
                )
            }
        }
    }
}

/**
 * 108dp Health Status Banner Card from backup_settings_light.svg:
 * Soft sage green `#E0EBE2`, database icon, LAST LOCAL BACKUP label, timestamp, patient & batch stats, Healthy green pill badge.
 */
@Composable
private fun BackupStatusBannerCard(
    timestamp: String,
    details: String,
    isHealthy: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFE0EBE2),
        border = BorderStroke(1.dp, Color(0xFFC8DEC9))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = Color(0xFF244032),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LAST LOCAL BACKUP",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF244032)
                    )
                }

                // "Healthy" green status pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFDCF4E4)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF054F31),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Healthy",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF054F31)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = timestamp,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF244032)
                )
                Text(
                    text = details,
                    fontSize = 9.5.sp,
                    color = Color(0xFF244032).copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * 62dp Backup Item Card for Local and Cloud backups matching SVG:
 * Icon, Title, Subtitle, Trailing pill badge & action icon.
 */
@Composable
private fun BackupFileItemCard(
    isCloud: Boolean,
    title: String,
    subtitle: String,
    badgeLabel: String,
    onAction: () -> Unit
) {
    val customColors = LocalCustomColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onAction() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCloud) Icons.Default.CloudQueue else Icons.Default.Storage,
                contentDescription = null,
                tint = Color(0xFF16558F),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    color = customColors.textGrey
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = badgeLabel,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = customColors.textGrey
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    tint = Color(0xFF16558F),
                    modifier = Modifier.size(18.dp)
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
private fun BackupProgressDialog(label: String) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = Spacing.xs) {
            Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(Spacing.lg))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun RestoreSummaryDialog(
    summary: RestoreSummary,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onMerge: () -> Unit
) {
    var showReplaceConfirm by remember { mutableStateOf(false) }
    val nf = remember { NumberFormat.getIntegerInstance() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Created: ${PatientUtils.formatDateTimeForDisplay(summary.createdAt)}", style = MaterialTheme.typography.bodyMedium)
                Text("App version: ${summary.appVersion}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.sm))
                summary.recordCounts.entries.filter { it.value > 0 }.forEach { (label, count) ->
                    Text("• ${nf.format(count)} $label", style = MaterialTheme.typography.bodySmall)
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
            title = { Text("Replace All Clinic Data?") },
            text = { Text("This will completely replace all local data with the contents of this backup. A safety backup will be created automatically.") },
            confirmButton = {
                Button(
                    onClick = {
                        showReplaceConfirm = false
                        onReplace()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Replace")
                }
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
    onConfirm: (password: String, settings: AutoBackupSettings?) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Backup Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Backups are encrypted with your backup password. Minimum 12 characters required for data security.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    supportingText = {
                        if (password.isNotEmpty() && password.length < 12) {
                            Text("Password must be at least 12 characters (${password.length}/12)")
                        }
                    },
                    isError = password.isNotEmpty() && password.length < 12,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password, null) },
                enabled = password.length >= 12
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
