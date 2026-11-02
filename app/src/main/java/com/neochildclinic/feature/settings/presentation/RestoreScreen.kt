package com.neochildclinic.feature.settings.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun RestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val customColors = LocalCustomColors.current

    var pendingAction by remember { mutableStateOf<PendingPasswordAction?>(null) }
    var showUndoConfirm by remember { mutableStateOf(false) }

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
                            text = "Restore Backups",
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
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
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
                // Section 1: Offline / Local Restore
                item {
                    Text(
                        text = "OFFLINE RESTORE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = customColors.textGrey,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                // Pick Local File Card
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { importLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE0EBE2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = Color(0xFF244032),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Browse local backup file",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Select a .nccb backup file from device storage",
                                    fontSize = 11.sp,
                                    color = customColors.textGrey
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = customColors.textGrey
                            )
                        }
                    }
                }

                // Safety Snapshot (if available)
                if (state.hasSafetyBackup) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showUndoConfirm = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFCE8E6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFFC5221F),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Restore safety snapshot",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Data copy taken just before your last restore",
                                        fontSize = 11.sp,
                                        color = customColors.textGrey
                                    )
                                }
                                TextButton(onClick = { showUndoConfirm = true }) {
                                    Text("Restore", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Section 2: Cloudflare Cloud Backups
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CLOUDFLARE BACKUPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = customColors.textGrey,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        TextButton(onClick = { viewModel.refreshCloudBackups() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh", fontSize = 12.sp)
                        }
                    }
                }

                if (!state.cloudConfigured) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Cloud backup not configured",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Configure your Cloudflare R2 credentials in environment settings to sync and restore cloud backups.",
                                    fontSize = 11.sp,
                                    color = customColors.textGrey
                                )
                            }
                        }
                    }
                } else if (state.cloudBackupsLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                } else if (state.cloudBackupsError != null) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Failed to load cloud backups",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.cloudBackupsError ?: "",
                                    fontSize = 11.sp,
                                    color = customColors.textGrey
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { viewModel.refreshCloudBackups() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                } else if (state.cloudBackups.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No cloud backups available.",
                                    fontSize = 12.sp,
                                    color = customColors.textGrey
                                )
                            }
                        }
                    }
                } else {
                    items(state.cloudBackups) { cloudBackup ->
                        CloudBackupItemCard(
                            backup = cloudBackup,
                            onRestore = { pendingAction = PendingPasswordAction.CloudRestore(cloudBackup.backupId) },
                            onDelete = { viewModel.deleteCloudBackup(cloudBackup.backupId) }
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
                    title = { Text("Restore safety snapshot?") },
                    text = { Text("This will replace current clinic data with the safety backup taken just before your last restore.") },
                    confirmButton = {
                        TextButton(onClick = { showUndoConfirm = false; viewModel.restoreSafetyBackup() }) { Text("Restore") }
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
                    title = { Text(if (msg.isError) "Restore" else "Success") },
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
                            is PendingPasswordAction.ImportLocal -> viewModel.peekLocalBackup(action.uri, password.toCharArray())
                            is PendingPasswordAction.CloudRestore -> viewModel.peekCloudBackup(action.backupId, password.toCharArray())
                            else -> {}
                        }
                        pendingAction = null
                    }
                )
            }
        }
    }
}

@Composable
private fun CloudBackupItemCard(
    backup: CloudBackupMetadata,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val customColors = LocalCustomColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE3EEF8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudQueue,
                    contentDescription = null,
                    tint = Color(0xFF16558F),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PatientUtils.formatDateTimeForDisplay(backup.createdAt),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${PatientUtils.formatBytes(backup.sizeBytes)} · v${backup.appVersion}",
                    fontSize = 10.sp,
                    color = customColors.textGrey
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = customColors.textGrey,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    onClick = onRestore,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16558F)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Cloud Backup?") },
            text = { Text("This will permanently remove this backup from Cloudflare R2.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
