package com.neochildclinic.feature.sync.presentation

import com.neochildclinic.core.designsystem.*

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
import com.neochildclinic.domain.model.SyncItem
import com.neochildclinic.domain.model.SyncStatus
import com.neochildclinic.domain.model.SyncErrorDetails
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val refreshError by viewModel.refreshError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var wasRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) {
            // Surface a failed import instead of always claiming success (finding 15).
            snackbarHostState.showSnackbar(refreshError ?: "Cloud data imported successfully")
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
                        containerColor = if (isInSelectionMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
                        titleContentColor = if (isInSelectionMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = if (isInSelectionMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = if (isInSelectionMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onBackground
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
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
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

                        item { Spacer(Modifier.height(Spacing.lg)) }
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
    val pending = (total - failed).coerceAtLeast(0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.card, horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$pending",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (pending > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.outline
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$total",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Total Queue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VerticalDivider(
                modifier = Modifier.height(36.dp),
                color = MaterialTheme.colorScheme.outline
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$failed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (failed > 0) MaterialTheme.colorScheme.error else Color(0xFF067647)
                )
                Text(
                    text = "Failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                item.status == SyncStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                item.status == SyncStatus.SYNCING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = when (item.status) {
                            SyncStatus.SYNCED -> Color(0xFFDCF4E4)
                            SyncStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                            SyncStatus.SYNCING -> MaterialTheme.colorScheme.primaryContainer
                            SyncStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (item.status) {
                                    SyncStatus.SYNCED -> Icons.Default.Check
                                    SyncStatus.FAILED -> Icons.Default.ErrorOutline
                                    SyncStatus.SYNCING -> Icons.Default.Sync
                                    SyncStatus.PENDING -> Icons.Default.Schedule
                                },
                                contentDescription = null,
                                tint = getStatusColor(item.status),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Text("${item.entityName} Record", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        color = when (item.status) {
                            SyncStatus.SYNCED -> Color(0xFFDCF4E4)
                            SyncStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                            SyncStatus.SYNCING -> MaterialTheme.colorScheme.primaryContainer
                            SyncStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = item.status.name,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = getStatusColor(item.status)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(Spacing.xs))
            Text("Operation: ${item.operation.name}", style = MaterialTheme.typography.bodySmall)
            Text("ID: ${item.entityId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            if (item.status == SyncStatus.FAILED) {
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(Modifier.height(Spacing.sm))
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
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            "URL: $url",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (errorDetails.headers.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.xs))
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
                    Spacer(Modifier.height(Spacing.sm))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Failed: ${formatTimestampForDisplay(Date(item.updatedAt))}", style = MaterialTheme.typography.labelSmall)
                        Text("Retries: ${item.retryCount}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun getStatusColor(status: SyncStatus): Color = when (status) {
    SyncStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    SyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
    SyncStatus.SYNCED -> Color(0xFF067647)
    SyncStatus.FAILED -> MaterialTheme.colorScheme.error
}

private fun formatTimestampForDisplay(date: Date): String {
    return DateTimeFormatter.ofPattern("dd MMM, HH:mm:ss", Locale.getDefault())
        .withZone(ZoneId.of("Asia/Kolkata"))
        .format(date.toInstant())
}
