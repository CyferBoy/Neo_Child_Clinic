package com.neochildclinic.feature.audit.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.EmptyState
import com.neochildclinic.core.ui.ErrorState
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.domain.model.AuditLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullAuditLogScreen(
    onBack: () -> Unit,
    viewModel: FullAuditLogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = remember { listOf("All", "Patients", "Inventory", "Finance", "Staff") }

    val filteredLogs = remember(uiState.logs, selectedFilter) {
        if (selectedFilter == "All") {
            uiState.logs
        } else {
            uiState.logs.filter { log ->
                when (selectedFilter) {
                    "Patients" -> log.module.contains("patient", ignoreCase = true) || log.entityType.contains("patient", ignoreCase = true)
                    "Inventory" -> log.module.contains("inventory", ignoreCase = true) || log.module.contains("vaccine", ignoreCase = true) || log.entityType.contains("batch", ignoreCase = true)
                    "Finance" -> log.module.contains("finance", ignoreCase = true) || log.module.contains("expense", ignoreCase = true) || log.module.contains("payment", ignoreCase = true)
                    "Staff" -> log.module.contains("staff", ignoreCase = true) || log.module.contains("user", ignoreCase = true)
                    else -> true
                }
            }
        }
    }

    AppBackground {
        Scaffold(
            topBar = {
                BackTopAppBar(
                    title = { Text("Audit Log") },
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
                    ErrorState(
                        message = uiState.error.orEmpty(),
                        onRetry = viewModel::refresh
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
                        contentPadding = PaddingValues(bottom = 80.dp, top = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // 1. Filter Pills Row (matches audit_log_light.svg: 36dp height, 18dp rx)
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.screen)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                filterOptions.forEach { filter ->
                                    val isSelected = selectedFilter == filter
                                    Surface(
                                        onClick = { selectedFilter = filter },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        ) {
                                            Text(
                                                text = filter,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Section Eyebrow Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.screen, vertical = Spacing.xxs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RECENT EVENTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${filteredLogs.size} events",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 3. Audit Log Items or Empty State
                        if (filteredLogs.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    title = "No audit events found",
                                    message = "No actions match the selected filter."
                                )
                            }
                        } else {
                            items(filteredLogs, key = { it.id }) { log ->
                                Box(modifier = Modifier.padding(horizontal = Spacing.screen)) {
                                    AuditLogItem(log)
                                }
                            }
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

                        // 4. Retention note at bottom
                        item {
                            Text(
                                text = "Audit events are retained for 90 days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogItem(log: AuditLog) {
    val (icon, iconContainer, iconTint) = getModuleVisuals(log.module, log.action)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Icon Container (matches audit_log_light.svg: 30dp circle)
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = iconContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = iconTint
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${log.action} ${log.entityType}".trim(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = PatientUtils.formatDateTimeForDisplay(log.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!log.remarks.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = log.remarks.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.user,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private data class ModuleVisual(val icon: ImageVector, val container: Color, val tint: Color)

@Composable
private fun getModuleVisuals(module: String, action: String): ModuleVisual {
    val mod = module.uppercase()
    val act = action.uppercase()

    return when {
        mod.contains("PATIENT") -> ModuleVisual(
            icon = Icons.Default.Person,
            container = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        mod.contains("VACCINE") || mod.contains("VACCINATION") -> ModuleVisual(
            icon = Icons.Default.Vaccines,
            container = MaterialTheme.colorScheme.primaryContainer,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        mod.contains("INVENTORY") || mod.contains("BATCH") -> ModuleVisual(
            icon = Icons.Default.Inventory2,
            container = MaterialTheme.colorScheme.secondaryContainer,
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        mod.contains("FINANCE") || mod.contains("EXPENSE") -> ModuleVisual(
            icon = Icons.Default.ReceiptLong,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        mod.contains("WASTE") || act.contains("DELETE") -> ModuleVisual(
            icon = Icons.Default.Delete,
            container = MaterialTheme.colorScheme.errorContainer,
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        mod.contains("SECURITY") || mod.contains("AUTH") -> ModuleVisual(
            icon = Icons.Default.Security,
            container = MaterialTheme.colorScheme.surfaceVariant,
            tint = MaterialTheme.colorScheme.primary
        )
        else -> ModuleVisual(
            icon = Icons.Default.History,
            container = MaterialTheme.colorScheme.surfaceVariant,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
