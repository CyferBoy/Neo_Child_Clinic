package com.neochildclinic.feature.inventory.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.common.PatientUtils.formatDateForDisplay
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.*
import com.neochildclinic.domain.model.InventoryFilter
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.InventorySort
import com.neochildclinic.domain.model.VaccineBatch
import com.neochildclinic.feature.inventory.domain.InventoryUtils
import kotlinx.coroutines.launch

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

    DeleteConfirmationDialog(
        show = batchToDelete != null,
        onDismiss = { batchToDelete = null },
        onConfirm = {
            batchToDelete?.let {
                viewModel.deleteBatch(it.batchId) { error ->
                    scope.launch { snackbarHostState.showSnackbar(error) }
                }
            }
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
                            Icon(Icons.Default.History, contentDescription = "Stock History", tint = MaterialTheme.colorScheme.onSurface)
                        }
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
                                    Text("New Vaccine", fontWeight = FontWeight.SemiBold)
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
                                    Text("Add Stock", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
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
                        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp, top = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // 1. Filter Chips Row
                        item {
                            val filterOptions = listOf(
                                InventoryFilter.ALL to "All",
                                InventoryFilter.LOW_STOCK to "Low stock",
                                InventoryFilter.NEAR_EXPIRY to "Near expiry",
                                InventoryFilter.OUT_OF_STOCK to "Out of stock"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.screen)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                filterOptions.forEach { (filter, label) ->
                                    val isSelected = uiState.filter == filter
                                    Surface(
                                        onClick = { onFilterChange(filter) },
                                        shape = RoundedCornerShape(15.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 14.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Stats Strip Card
                        item {
                            val totalBatches = remember(uiState.inventory) {
                                uiState.inventory.sumOf { it.batches.count { b -> b.remainingQuantity > 0 } }
                            }
                            val totalStockValue = remember(uiState.inventory) {
                                uiState.inventory.sumOf { item ->
                                    item.batches.sumOf { b -> b.purchaseCost * b.remainingQuantity }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.screen),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "${uiState.inventory.size}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "vaccine types",
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
                                            text = "$totalBatches",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "active batches",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                if (uiState.showStockValueEnabled) {
                                    VerticalDivider(
                                        modifier = Modifier.height(36.dp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = com.neochildclinic.core.common.CurrencyUtils.formatInr(totalStockValue),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "stock value",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Vaccine List or Empty State
                        if (uiState.inventory.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = if (searchQuery.isEmpty()) Icons.Default.Inventory2 else Icons.Default.SearchOff,
                                    title = if (searchQuery.isEmpty()) "No inventory yet" else "No vaccines match \"$searchQuery\"",
                                    message = if (searchQuery.isEmpty()) "Add a vaccine definition and its first batch to start tracking stock."
                                    else "Try a different search term, or clear the filter."
                                )
                            }
                        } else {
                            items(uiState.inventory, key = { it.id }) { item ->
                                Box(modifier = Modifier.padding(horizontal = Spacing.screen)) {
                                    VaccineItemCard(
                                        item = item,
                                        hideExpiredEmpty = uiState.hideExpiredEmpty,
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaccineItemCard(
    item: InventoryItem,
    hideExpiredEmpty: Boolean = true,
    onAddBatch: (String, String) -> Unit,
    onEditBatch: (String, String, String) -> Unit,
    onDeleteBatch: (VaccineBatch) -> Unit,
    onEditVaccine: (String) -> Unit,
    onDeleteVaccine: (InventoryItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var overrideHideBatches by remember(hideExpiredEmpty) { mutableStateOf<Boolean?>(null) }
    val isHiding = overrideHideBatches ?: hideExpiredEmpty

    val unusableBatches = remember(item.batches) {
        item.batches.filter { InventoryUtils.isExpired(it.expiryDate) || it.remainingQuantity <= 0 }
    }

    val visibleBatches = if (isHiding) item.batches.filterNot { it in unusableBatches } else item.batches

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Status icon box
                    val iconContainerColor = when {
                        item.hasExpired || item.stock <= 0 -> MaterialTheme.colorScheme.errorContainer
                        item.isLowStock || item.isNearExpiry -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                    val iconContentColor = when {
                        item.hasExpired || item.stock <= 0 -> MaterialTheme.colorScheme.onErrorContainer
                        item.isLowStock || item.isNearExpiry -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }

                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = iconContainerColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Vaccines,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = iconContentColor
                            )
                        }
                    }

                    Spacer(Modifier.width(Spacing.md))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.brandName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val batchCountText = if (isHiding && unusableBatches.isNotEmpty()) {
                            "${visibleBatches.size} active · ${item.batches.size} total"
                        } else {
                            "${item.batches.size} batches"
                        }
                        Text(
                            text = listOfNotNull(
                                item.company.takeIf { it.isNotBlank() },
                                batchCountText
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.width(Spacing.sm))

                    // Stock count & pill
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${item.stock}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (item.stock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "doses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        val (statusText, statusBg, statusFg) = when {
                            item.hasExpired -> Triple("Expired", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                            item.stock <= 0 -> Triple("Out of stock", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                            item.isLowStock -> Triple("Low stock", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                            item.isNearExpiry -> Triple("Near expiry", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                            else -> Triple("In stock", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = statusBg
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusFg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (unusableBatches.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text(if (isHiding) "Show Hidden Batches (${unusableBatches.size})" else "Hide Expired & Empty") },
                            onClick = {
                                val nextHiding = !isHiding
                                overrideHideBatches = nextHiding
                                menuExpanded = false
                                if (!nextHiding) expanded = true
                            },
                            leadingIcon = { Icon(if (isHiding) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) }
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
                Column(modifier = Modifier.padding(top = Spacing.md)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Total Batches: ${item.batches.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Active Batches: ${item.activeBatchesCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onAddBatch(item.id, item.brandName) }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Batch", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(Spacing.xs))

                    visibleBatches.forEach { batch ->
                        BatchRow(batch, onEditBatch, onDeleteBatch, item.brandName)
                    }

                    if (visibleBatches.isEmpty() && item.batches.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No active batches. Long press to show hidden.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
    val isExpired = InventoryUtils.isExpired(batch.expiryDate)
    val isZeroQty = batch.remainingQuantity <= 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Batch: ${batch.batchNumber}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isExpired) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Expired",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                } else if (isZeroQty) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Empty",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                "Exp: ${formatDateForDisplay(batch.expiryDate)} · Qty: ${batch.remainingQuantity}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "MRP: ₹${batch.sellingPrice} · Net: ₹${batch.purchaseCost}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Batch actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun SortButton(currentSort: InventorySort, onSortSelected: (InventorySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Sort, "Sort", tint = MaterialTheme.colorScheme.onSurface)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InventorySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.name.replace("_", " ").lowercase().replaceFirstChar { it.titlecase() }) },
                    onClick = { onSortSelected(sort); expanded = false },
                    trailingIcon = { if (currentSort == sort) Icon(Icons.Default.Check, null) }
                )
            }
        }
    }
}
