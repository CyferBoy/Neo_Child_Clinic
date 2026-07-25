package com.clinic.neochild.features.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast
import androidx.compose.ui.unit.dp
import com.clinic.neochild.core.ui.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.core.designsystem.NeoChildTheme
import com.clinic.neochild.core.utils.InventoryUtils
import com.clinic.neochild.core.utils.PatientUtils.formatDateForDisplay
import com.clinic.neochild.data.local.entity.VaccineBatchEntity
import com.clinic.neochild.domain.model.InventoryFilter
import com.clinic.neochild.domain.model.InventoryItem
import com.clinic.neochild.domain.model.InventorySort

@Composable
fun VaccineInventoryScreen(
    onBack: () -> Unit = {},
    onAddVaccine: () -> Unit = {},
    onEditVaccine: (String) -> Unit = {},
    onAddBatch: (String, String) -> Unit = { _, _ -> },
    onEditBatch: (String, String, String) -> Unit = { _, _, _ -> },
    viewModel: VaccineInventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var batchToDelete by remember { mutableStateOf<VaccineBatchEntity?>(null) }
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
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                }
            }
            vaccineToDelete = null
        },
        title = "Delete Vaccine",
        message = "Are you sure you want to delete ${vaccineToDelete?.brandName}? If history exists, it will be archived instead."
    )

    VaccineInventoryContent(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onSortChange = viewModel::onSortChange,
        onAddVaccine = onAddVaccine,
        onEditVaccine = onEditVaccine,
        onAddBatch = onAddBatch,
        onEditBatch = onEditBatch,
        onDeleteBatch = { batchToDelete = it },
        onDeleteVaccine = { vaccineToDelete = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccineInventoryContent(
    uiState: VaccineInventoryUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (InventoryFilter) -> Unit,
    onSortChange: (InventorySort) -> Unit,
    onAddVaccine: () -> Unit,
    onEditVaccine: (String) -> Unit,
    onAddBatch: (String, String) -> Unit,
    onEditBatch: (String, String, String) -> Unit,
    onDeleteBatch: (VaccineBatchEntity) -> Unit,
    onDeleteVaccine: (InventoryItem) -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SearchTopAppBar(
                    title = "Inventory",
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    onBack = onBack,
                    actions = {
                        FilterButton(currentFilter = uiState.filter, onFilterSelected = onFilterChange)
                        SortButton(currentSort = uiState.sort, onSortSelected = onSortChange)
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddVaccine) {
                    Icon(Icons.Default.Add, "Add Stock")
                }
            }
        ) { padding ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(padding)
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.inventory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (uiState.searchQuery.isEmpty()) "No inventory found" else "No results found")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
    onDeleteBatch: (VaccineBatchEntity) -> Unit,
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
                item.hasExpired -> Color(0xFFFFEBEE)
                item.isLowStock -> Color(0xFFFFF3E0)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        text = { Text("Delete Vaccine", color = Color.Red) },
                        onClick = {
                            menuExpanded = false
                            onDeleteVaccine(item)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Batches: ${item.batches.size}", style = MaterialTheme.typography.labelMedium)
                            Text("Active Batches: ${item.activeBatchesCount}", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onAddBatch(item.id, item.brandName) }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Batch")
                        }
                    }
                    
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    visibleBatches.forEach { batch ->
                        BatchRow(batch, onEditBatch, onDeleteBatch, item.brandName)
                    }

                    if (visibleBatches.isEmpty() && item.batches.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("No active batches. Long press to show hidden.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRow(
    batch: VaccineBatchEntity,
    onEditBatch: (String, String, String) -> Unit,
    onDeleteBatch: (VaccineBatchEntity) -> Unit,
    brandName: String
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Batch: ${batch.batchNumber}", fontWeight = FontWeight.Bold)
            Text("Exp: ${formatDateForDisplay(batch.expiryDate)} • Qty: ${batch.remainingQuantity}")
            Text("MRP: ₹${batch.sellingPrice} • Net: ₹${batch.purchaseCost}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, null)
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { menuExpanded = false; onEditBatch(batch.batchId, batch.vaccineId, brandName) }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color.Red) },
                    onClick = { menuExpanded = false; onDeleteBatch(batch) }
                )
            }
        }
    }
}

@Composable
private fun StockStatusBadge(item: InventoryItem) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "${item.stock}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = if (item.isLowStock || item.hasExpired) Color.Red else MaterialTheme.colorScheme.primary
        )
        if (item.hasExpired) Text("EXPIRED", color = Color.Red, style = MaterialTheme.typography.labelSmall)
        else if (item.isLowStock) Text("LOW STOCK", color = Color(0xFFE65100), style = MaterialTheme.typography.labelSmall)
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
