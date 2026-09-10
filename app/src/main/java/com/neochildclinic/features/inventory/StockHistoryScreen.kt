package com.neochildclinic.features.inventory

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.utils.PatientUtils.formatDateTimeForDisplay
import com.neochildclinic.data.local.entity.InventoryTransactionEntity
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.StockHistoryTypeFilter
import com.neochildclinic.domain.model.displayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockHistoryScreen(
    onBack: () -> Unit = {},
    viewModel: StockHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }


    val filtersActive = uiState.selectedVaccineId != null || uiState.selectedBatchId != null ||
        uiState.selectedTypeFilter != StockHistoryTypeFilter.ALL ||
        uiState.fromDate.isNotBlank() || uiState.toDate.isNotBlank()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Stock History") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                if (filtersActive) Icons.Default.FilterAlt else Icons.Default.FilterAltOff,
                                contentDescription = "Filters",
                                tint = if (filtersActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                AnimatedVisibilityFilters(visible = showFilters) {
                    StockHistoryFilters(
                        uiState = uiState,
                        onVaccineFilterChange = viewModel::onVaccineFilterChange,
                        onBatchFilterChange = viewModel::onBatchFilterChange,
                        onDateRangeChange = viewModel::onDateRangeChange,
                        onClearFilters = viewModel::clearFilters
                    )
                }

                TransactionTypeChipRow(
                    selected = uiState.selectedTypeFilter,
                    onSelected = viewModel::onTypeFilterChange
                )

                AppPullToRefresh(
                    isRefreshing = uiState.isLoading,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!uiState.isLoading && uiState.transactions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (filtersActive) "No stock movements match these filters" else "No stock movements yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()

                        LaunchedEffect(listState, uiState.transactions.size, uiState.canLoadMore) {
                            snapshotFlow {
                                val layout = listState.layoutInfo
                                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                                lastVisible >= uiState.transactions.size - 5
                            }.collect { shouldLoad ->
                                if (shouldLoad && uiState.canLoadMore && !uiState.isLoadingMore) {
                                    viewModel.loadMore()
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.transactions, key = { it.transactionId }) { transaction ->
                                StockHistoryEntryCard(
                                    transaction = transaction,
                                    vaccineName = uiState.vaccineLabelById[transaction.vaccineId] ?: transaction.vaccineId,
                                    batchNumber = uiState.batchLabelById[transaction.batchId] ?: transaction.batchId
                                )
                            }
                            if (uiState.isLoadingMore) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedVisibilityFilters(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(visible = visible) {
        content()
    }
}

@Composable
private fun LoadMoreRow(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        } else {
            TextButton(onClick = onClick) {
                Text("Load More")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockHistoryFilters(
    uiState: StockHistoryUiState,
    onVaccineFilterChange: (String?) -> Unit,
    onBatchFilterChange: (String?) -> Unit,
    onDateRangeChange: (String, String) -> Unit,
    onClearFilters: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var vaccineExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = vaccineExpanded,
            onExpandedChange = { vaccineExpanded = !vaccineExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = uiState.selectedVaccineName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Vaccine") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vaccineExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = vaccineExpanded, onDismissRequest = { vaccineExpanded = false }) {
                DropdownMenuItem(text = { Text("All Vaccines") }, onClick = {
                    onVaccineFilterChange(null)
                    vaccineExpanded = false
                })
                uiState.vaccines.forEach { vaccine ->
                    DropdownMenuItem(text = { Text(vaccine.brandName) }, onClick = {
                        onVaccineFilterChange(vaccine.id)
                        vaccineExpanded = false
                    })
                }
            }
        }

        var batchExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = batchExpanded,
            onExpandedChange = { if (uiState.selectedVaccineId != null) batchExpanded = !batchExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = uiState.selectedBatchLabel,
                onValueChange = {},
                readOnly = true,
                enabled = uiState.selectedVaccineId != null,
                label = { Text("Batch") },
                placeholder = { Text("Select a vaccine first") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = batchExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = batchExpanded, onDismissRequest = { batchExpanded = false }) {
                DropdownMenuItem(text = { Text("All Batches") }, onClick = {
                    onBatchFilterChange(null)
                    batchExpanded = false
                })
                uiState.batchesForSelectedVaccine.forEach { batch ->
                    DropdownMenuItem(text = { Text(batch.batchNumber) }, onClick = {
                        onBatchFilterChange(batch.batchId)
                        batchExpanded = false
                    })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateDropdownPicker(
                label = "From",
                currentDate = uiState.fromDate,
                onDateSelected = { onDateRangeChange(it, uiState.toDate) },
                modifier = Modifier.weight(1f)
            )
            DateDropdownPicker(
                label = "To",
                currentDate = uiState.toDate,
                onDateSelected = { onDateRangeChange(uiState.fromDate, it) },
                modifier = Modifier.weight(1f)
            )
        }

        TextButton(onClick = onClearFilters, modifier = Modifier.align(Alignment.End)) {
            Text("Clear Filters")
        }

        HorizontalDivider()
    }
}

@Composable
private fun TransactionTypeChipRow(
    selected: StockHistoryTypeFilter,
    onSelected: (StockHistoryTypeFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StockHistoryTypeFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                label = { Text(filter.label) }
            )
        }
    }
}

@Composable
private fun StockHistoryEntryCard(
    transaction: InventoryTransactionEntity,
    vaccineName: String,
    batchNumber: String
) {
    val type = runCatching { InventoryTransactionType.valueOf(transaction.transactionType) }.getOrNull()
    val isPositive = transaction.quantity >= 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(vaccineName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Batch: $batchNumber", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = "${if (isPositive) "+" else ""}${transaction.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
            }

            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(type?.displayLabel() ?: transaction.transactionType) }
            )

            Text(
                formatDateTimeForDisplay(transaction.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!transaction.notes.isNullOrBlank()) {
                Text("Reason: ${transaction.notes}", style = MaterialTheme.typography.bodySmall)
            }

            val reference = transaction.visitId ?: transaction.patientId
            if (!reference.isNullOrBlank()) {
                Text("Reference: $reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("By: ${transaction.user}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (!transaction.isSynced) {
                Text("Sync Pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}
