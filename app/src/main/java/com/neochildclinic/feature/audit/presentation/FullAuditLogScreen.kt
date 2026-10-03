package com.neochildclinic.feature.audit.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.EmptyState
import com.neochildclinic.core.ui.ErrorState
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.domain.model.AuditLog
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullAuditLogScreen(
    onBack: () -> Unit,
    viewModel: FullAuditLogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    AppBackground {
        Scaffold(
            topBar = {
                BackTopAppBar(
                    title = { Text("Clinic Audit Logs") },
                    onBack = onBack
                )
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                if (uiState.isLoading && uiState.logs.isEmpty()) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 8,
                        cardShaped = true,
                        spacing = Spacing.sm,
                        contentPadding = PaddingValues(Spacing.lg)
                    )
                } else if (uiState.error != null && uiState.logs.isEmpty()) {
                    // Guard on logs.isEmpty(): the ViewModel deliberately keeps existing rows
                    // on a refresh failure (FullAuditLogViewModel.kt:63), and loadMore() writes
                    // the same `error` field. Without this guard a failed pull-to-refresh or a
                    // failed page-2 fetch blanked out rows the user was already reading.
                    val message = uiState.error
                    ErrorState(
                        message = message.orEmpty(),
                        onRetry = viewModel::refresh
                    )
                } else if (uiState.logs.isEmpty()) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "No audit logs found",
                        message = "Actions taken in the app will be recorded here."
                    )
                } else {
                    val listState = rememberLazyListState()

                    LaunchedEffect(listState, uiState.logs.size, uiState.hasMore) {
                        snapshotFlow {
                            val layout = listState.layoutInfo
                            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                            lastVisible >= uiState.logs.size - 5
                        }.collect { shouldLoadMore ->
                            if (shouldLoadMore) viewModel.loadMore()
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(uiState.logs, key = { it.id }) { log ->
                            AuditLogItem(log)
                        }

                        if (uiState.isLoadingMore) {
                            item(key = "audit_loading_more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        // Non-fatal: the rows above stay readable, and the retry is the
                        // scroll trigger firing loadMore() again.
                        if (uiState.error != null) {
                            item(key = "audit_load_more_error") {
                                ErrorState(
                                    message = "Couldn't load more: ${uiState.error}",
                                    onRetry = viewModel::loadMore,
                                    retryLabel = "Retry",
                                    modifier = Modifier.padding(vertical = Spacing.sm)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogItem(log: AuditLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = getModuleColor(log.module),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = log.module,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                Text(
                    text = PatientUtils.formatDateTimeForDisplay(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(Spacing.sm))
            
            Text(
                text = "${log.action}: ${log.entityType}",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            
            if (log.remarks != null) {
                Text(
                    text = log.remarks!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(Spacing.xs))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "User: ${log.user}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (log.device != null) {
                Text(
                    text = "Device: ${log.device}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

fun getModuleColor(module: String): Color = when (module.uppercase()) {
    "PATIENT" -> Color(0xFF2196F3)
    "VACCINE", "INVENTORY" -> Color(0xFFFF9800)
    "FINANCE" -> Color(0xFF4CAF50)
    "STAFF", "USERS" -> Color(0xFF9C27B0)
    "SYSTEM", "SYNC" -> Color(0xFF607D8B)
    else -> Color(0xFF757575)
}
