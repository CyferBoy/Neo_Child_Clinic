package com.neochildclinic.feature.inventory.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.*
import com.neochildclinic.core.designsystem.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.feature.inventory.domain.InventoryUtils
import com.neochildclinic.core.common.PatientUtils.formatDateForDisplay
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.domain.model.VaccineBatch

@Composable
fun VaccineInventoryScreen(
    onBack: () -> Unit = {},
    onAddVaccine: () -> Unit = {},
    onEditVaccine: (String) -> Unit = {},
    onAddBatch: (String, String) -> Unit = { _, _ -> },
    onEditBatch: (String, String, String) -> Unit = { _, _, _ -> },
    onAddStock: () -> Unit = {},
    onStockHistory: () -> Unit = {},
    viewModel: VaccineInventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var batchToDelete by remember { mutableStateOf<VaccineBatch?>(null) }
    var vaccineToDelete by remember { mutableStateOf<InventoryItem?>(null) }
    val context = LocalContext.current

    DeleteConfirmationDialog(
        show = batchToDelete != null,
        onDismiss = { batchToDelete = null },
        onConfirm = {
            batchToDelete?.let { viewModel.deleteBatch(it.batchId) }
            batchToDelete = null
        },
        title = "Delete Batch",
        message = "Are you sure you want to delete Batch ${batchToDelete?.batchNumber}?"
    )

    DeleteConfirmationDialog(
        show = vaccineToDelete != null,
        onDismiss = { vaccineToDelete = null },
        onConfirm = {
            vaccineToDelete?.let { 
                viewModel.deleteVaccine(it.id) { error ->
                    scope.launch { snackbarHostState.showSnackbar(error) }
                }
            }
            vaccineToDelete = null
        },
        title = "Delete Vaccine",
        message = "Are you sure you want to delete ${vaccineToDelete?.brandName}? If history exists, it will be archived instead."
    )

    VaccineInventoryContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        searchQuery = searchQuery,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onSortChange = viewModel::onSortChange,
        onAddVaccine = onAddVaccine,
        onEditVaccine = onEditVaccine,
        onAddBatch = onAddBatch,
        onEditBatch = onEditBatch,
        onAddStock = onAddStock,
        onStockHistory = onStockHistory,
        onDeleteBatch = { batchToDelete = it },
        onDeleteVaccine = { vaccineToDelete = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccineInventoryContent(
    uiState: VaccineInventoryUiState,
    snackbarHostState: SnackbarHostState,
    searchQuery: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (InventoryFilter) -> Unit,
    onSortChange: (InventorySort) -> Unit,
    onAddVaccine: () -> Unit,
    onEditVaccine: (String) -> Unit,
    onAddBatch: (String, String) -> Unit,
    onEditBatch: (String, String, String) -> Unit,
    onAddStock: () -> Unit,
    onStockHistory: () -> Unit,
    onDeleteBatch: (VaccineBatch) -> Unit,
    onDeleteVaccine: (InventoryItem) -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    AppBackground {
        Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                SearchTopAppBar(
                    title = "Inventory",
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = onStockHistory) {
                            Icon(Icons.Default.History, contentDescription = "Stock History")
                        }
                        FilterButton(currentFilter = uiState.filter, onFilterSelected = onFilterChange)
                        SortButton(currentSort = uiState.sort, onSortSelected = onSortChange)
                    }
                )
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(visible = fabExpanded) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                            modifier = Modifier.padding(bottom = Spacing.md)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    onAddVaccine()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Vaccines, "New Vaccine", modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text("New Vaccine")
                                }
                            }

                            SmallFloatingActionButton(
                                onClick = {
                                    fabExpanded = false
                                    onAddStock()
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AddBusiness, "Add Stock", modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text("Add Stock")
                                }
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = if (fabExpanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (fabExpanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = if (fabExpanded) "Close" else "Add Menu"
                        )
                    }
                }
            }
        ) { padding ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(padding)
            ) {
                if (uiState.isLoading) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 8,
                        cardShaped = true,
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm)
                    )
                } else if (uiState.inventory.isEmpty()) {
                    EmptyState(
                        icon = if (searchQuery.isEmpty()) Icons.Default.Inventory2 else Icons.Default.SearchOff,
                        title = if (searchQuery.isEmpty()) "No inventory yet" else "No vaccines match \"$searchQuery\"",
                        message = if (searchQuery.isEmpty()) "Add a vaccine definition and its first batch to start tracking stock."
                        else "Try a different search term, or clear the filter."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        items(uiState.inventory, key = { it.id }) { item ->
                            VaccineItemCard(
                                item = item,
                                onAddBatch = onAddBatch,
                                onEditBatch = onEditBatch,
                                onDeleteBatch = onDeleteBatch,
                                onEditVaccine = onEditVaccine,
                                onDeleteVaccine = onDeleteVaccine
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaccineItemCard(
    item: InventoryItem,
    onAddBatch: (String, String) -> Unit,
    onEditBatch: (String, String, String) -> Unit,
    onDeleteBatch: (VaccineBatch) -> Unit,
    onEditVaccine: (String) -> Unit,
    onDeleteVaccine: (InventoryItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showHiddenBatches by remember { mutableStateOf(false) }

    val hiddenBatches = remember(item.batches) {
        item.batches.filter { InventoryUtils.isExpired(it.expiryDate) && it.remainingQuantity <= 0 }
    }

    val visibleBatches = if (showHiddenBatches) item.batches else item.batches.filterNot { it in hiddenBatches }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { menuExpanded = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                // Was #FFEBEE / #FFF3E0 - light pastels that stayed light in dark
                // mode, where onSurface is also light, so the card went blank.
                item.hasExpired -> MaterialTheme.colorScheme.errorContainer
                item.isLowStock -> LocalCustomColors.current.softOrange
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.brandName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(item.company, style = MaterialTheme.typography.bodySmall)
                        if (item.mrp > 0) {
                            Text(
                                text = "Standard MRP: ₹${item.mrp}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                    }
                    StockStatusBadge(item)
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (hiddenBatches.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (showHiddenBatches) "Hide Expired & Empty" else "Show Hidden Batches") },
                            onClick = {
                                showHiddenBatches = !showHiddenBatches
                                menuExpanded = false
                                if (showHiddenBatches) expanded = true
                            },
                            leadingIcon = { Icon(if (showHiddenBatches) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit Vaccine") },
                        onClick = {
                            menuExpanded = false
                            onEditVaccine(item.id)
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Vaccine", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDeleteVaccine(item)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = Spacing.lg)) {
                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Batches: ${item.batches.size}", style = MaterialTheme.typography.labelMedium)
                            Text("Active Batches: ${item.activeBatchesCount}", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onAddBatch(item.id, item.brandName) }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(Spacing.lg))
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Add Batch")
                        }
                    }

                    HorizontalDivider()
                    Spacer(Modifier.height(Spacing.sm))

                    visibleBatches.forEach { batch ->
                        BatchRow(batch, onEditBatch, onDeleteBatch, item.brandName)
                    }

                    if (visibleBatches.isEmpty() && item.batches.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                            Text("No active batches. Long press to show hidden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRow(
    batch: VaccineBatch,
    onEditBatch: (String, String, String) -> Unit,
    onDeleteBatch: (VaccineBatch) -> Unit,
    brandName: String
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Batch: ${batch.batchNumber}", fontWeight = FontWeight.Bold)
            Text("Exp: ${formatDateForDisplay(batch.expiryDate)} • Qty: ${batch.remainingQuantity}")
            Text("MRP: ₹${batch.sellingPrice} • Net: ₹${batch.purchaseCost}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Batch actions")
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { menuExpanded = false; onEditBatch(batch.batchId, batch.vaccineId, brandName) }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDeleteBatch(batch) }
                )
            }
        }
    }
}

@Composable
private fun StockStatusBadge(item: InventoryItem) {
    val customColors = LocalCustomColors.current
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "${item.stock}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = if (item.isLowStock || item.hasExpired) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
        )
        // Was raw red at labelSmall: 3.59:1, under the 4.5:1 AA floor for small text.
        if (item.hasExpired) {
            Text("EXPIRED", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        } else if (item.isLowStock) {
            Text("LOW STOCK", color = customColors.textOrange, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FilterButton(currentFilter: InventoryFilter, onFilterSelected: (InventoryFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.FilterList, "Filter")
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InventoryFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.name.replace("_", " ").lowercase().capitalize()) },
                    onClick = { onFilterSelected(filter); expanded = false },
                    trailingIcon = { if (currentFilter == filter) Icon(Icons.Default.Check, null) }
                )
            }
        }
    }
}

@Composable
private fun SortButton(currentSort: InventorySort, onSortSelected: (InventorySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Sort, "Sort")
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InventorySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.name.replace("_", " ").lowercase().capitalize()) },
                    onClick = { onSortSelected(sort); expanded = false },
                    trailingIcon = { if (currentSort == sort) Icon(Icons.Default.Check, null) }
                )
            }
        }
    }
}

private fun String.capitalize() = this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
