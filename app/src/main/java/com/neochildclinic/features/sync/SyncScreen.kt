package com.neochildclinic.features.sync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.model.SyncItem
import com.neochildclinic.core.model.SyncStatus
import com.neochildclinic.core.model.SyncErrorDetails
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    isAdmin: Boolean = false,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val syncQueue by viewModel.syncQueue.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var wasRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) {
            snackbarHostState.showSnackbar("Cloud data imported successfully")
        }
        wasRefreshing = isRefreshing
    }

    var selectedItems by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleteAll by remember { mutableStateOf(false) }
    
    val failedItems = remember(syncQueue) { syncQueue.filter { it.status == SyncStatus.FAILED } }
    val pendingItems = remember(syncQueue) { syncQueue.filter { it.status != SyncStatus.FAILED } }

    val isInSelectionMode = selectedItems.isNotEmpty()

    AppBackground {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { 
                        if (isInSelectionMode) Text("${selectedItems.size} Selected")
                        else Text("Cloud Synchronization") 
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isInSelectionMode) selectedItems = emptySet()
                            else onBack()
                        }) {
                            Icon(
                                if (isInSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        if (isInSelectionMode) {
                            IconButton(onClick = {
                                selectedItems.forEach { viewModel.retryItem(it) }
                                selectedItems = emptySet()
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry Selected")
                            }
                            IconButton(onClick = { 
                                isDeleteAll = false
                                showDeleteConfirm = true 
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                            }
                        } else {
                            IconButton(
                                onClick = { viewModel.forceRefreshFromCloud() },
                                enabled = !isRefreshing
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Force Import from Cloud")
                            }
                            if (failedItems.isNotEmpty()) {
                                IconButton(onClick = {
                                    isDeleteAll = true
                                    showDeleteConfirm = true
                                }) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete All Failed")
                                }
                                IconButton(onClick = { viewModel.retryAllFailed() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry All Failed")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isInSelectionMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                        titleContentColor = if (isInSelectionMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = if (isInSelectionMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = if (isInSelectionMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                if (isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SyncSummary(syncQueue.size, failedItems.size)
                    }

                    if (failedItems.isNotEmpty()) {
                        item {
                            Text(
                                "Failed Sync Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        items(failedItems, key = { "failed_${it.id}" }) { item ->
                            SyncItemCard(
                                item = item,
                                isAdmin = isAdmin,
                                isSelected = selectedItems.contains(item.id),
                                onToggleSelection = {
                                    selectedItems = if (selectedItems.contains(item.id)) {
                                        selectedItems - item.id
                                    } else {
                                        selectedItems + item.id
                                    }
                                }
                            )
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    if (pendingItems.isNotEmpty()) {
                        item {
                            Text(
                                "Pending/Recent Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(pendingItems, key = { "pending_${it.id}" }) { item ->
                            SyncItemCard(item = item, isAdmin = isAdmin)
                        }
                    }

                    if (syncQueue.isEmpty() && !isRefreshing) {
                        item {
                            Box(modifier = Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Sync queue is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Sync Task?") },
            text = { Text("This will remove the pending sync operation. The original data will NOT be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isDeleteAll) viewModel.deleteAllFailed()
                        else {
                            selectedItems.forEach { viewModel.deleteItem(it) }
                            selectedItems = emptySet()
                        }
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SyncSummary(total: Int, failed: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Total Queue", style = MaterialTheme.typography.labelMedium)
                Text("$total Items", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            if (failed > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Failed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    Text("$failed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SyncItemCard(
    item: SyncItem,
    isAdmin: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (item.status == SyncStatus.FAILED) onToggleSelection() },
                onLongClick = { if (item.status == SyncStatus.FAILED) onToggleSelection() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                item.status == SyncStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                item.status == SyncStatus.SYNCING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.entityName} Record", fontWeight = FontWeight.Bold)
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    Text(item.status.name, style = MaterialTheme.typography.labelSmall, color = getStatusColor(item.status))
                }
            }
            
            Spacer(Modifier.height(4.dp))
            Text("Operation: ${item.operation.name}", style = MaterialTheme.typography.bodySmall)
            Text("ID: ${item.entityId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            if (item.status == SyncStatus.FAILED) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.3f))
                Spacer(Modifier.height(8.dp))
                val errorDetails = remember(item.lastError) {
                    SyncErrorDetails.fromStoredError(item.lastError)
                }
                Text(
                    "Reason: ${errorDetails.reason}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )

                if (isAdmin) {
                    errorDetails.url?.takeIf { it.isNotBlank() }?.let { url ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "URL: $url",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (errorDetails.headers.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Headers:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        errorDetails.headers.forEach { (name, value) ->
                            Text(
                                "$name: $value",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Failed: ${formatDateForDisplay(Date(item.updatedAt))}", style = MaterialTheme.typography.labelSmall)
                        Text("Retries: ${item.retryCount}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun getStatusColor(status: SyncStatus): Color = when (status) {
    SyncStatus.PENDING -> Color.Gray
    SyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
    SyncStatus.SYNCED -> Color(0xFF4CAF50)
    SyncStatus.FAILED -> MaterialTheme.colorScheme.error
}

private fun formatDateForDisplay(date: Date): String {
    val sdf = java.text.SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault())
    return sdf.format(date)
}
