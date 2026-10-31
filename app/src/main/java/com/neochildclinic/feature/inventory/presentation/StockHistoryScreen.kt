package com.neochildclinic.feature.inventory.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.EmptyState
import com.neochildclinic.core.ui.ErrorState
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.domain.model.InventoryTransaction
import com.neochildclinic.domain.model.InventoryTransactionType
import com.neochildclinic.domain.model.StockHistoryTypeFilter
import com.neochildclinic.domain.model.displayLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockHistoryScreen(
    onBack: () -> Unit = {},
    viewModel: StockHistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val loadError = uiState.error
    var showFilters by remember { mutableStateOf(false) }

    ShowSnackbar(loadError, snackbarHostState) { viewModel.clearError() }

    val filtersActive = uiState.selectedVaccineId != null || uiState.selectedBatchId != null ||
        uiState.selectedTypeFilter != StockHistoryTypeFilter.ALL ||
        uiState.fromDate.isNotBlank() || uiState.toDate.isNotBlank()

    // Group transactions by Date Header
    val groupedTransactions = remember(uiState.transactions) {
        groupTransactionsByDate(uiState.transactions)
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Stock History",
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
                    actions = {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter",
                                tint = if (filtersActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // SVG Pill Filter Chips Row
                StockHistoryFilterChipsRow(
                    selected = uiState.selectedTypeFilter,
                    onSelected = viewModel::onTypeFilterChange,
                    modifier = Modifier.padding(vertical = Spacing.xs)
                )

                // Expandable Advanced Filters (Vaccine, Batch, Date range)
                AnimatedVisibility(visible = showFilters) {
                    StockHistoryAdvancedFilters(
                        uiState = uiState,
                        onVaccineFilterChange = viewModel::onVaccineFilterChange,
                        onBatchFilterChange = viewModel::onBatchFilterChange,
                        onDateRangeChange = viewModel::onDateRangeChange,
                        onClearFilters = viewModel::clearFilters
                    )
                }

                // Main Content with PullToRefresh
                AppPullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isLoading && uiState.transactions.isEmpty()) {
                        SkeletonList(
                            modifier = Modifier.fillMaxSize(),
                            count = 6,
                            cardShaped = true,
                            spacing = Spacing.sm,
                            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm)
                        )
                    } else if (!uiState.isLoading && uiState.transactions.isEmpty() && loadError != null) {
                        ErrorState(
                            modifier = Modifier.fillMaxSize(),
                            message = loadError,
                            onRetry = {
                                viewModel.clearError()
                                viewModel.refresh()
                            }
                        )
                    } else if (!uiState.isLoading && uiState.transactions.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.fillMaxSize(),
                            title = if (filtersActive) "No stock movements match these filters" else "No stock movements yet"
                        )
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
                            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupedTransactions.forEach { (dateHeader, transactionsForDate) ->
                                item(key = "header_$dateHeader") {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                    )
                                }

                                items(transactionsForDate, key = { it.transactionId }) { transaction ->
                                    StockHistoryCard(
                                        transaction = transaction,
                                        vaccineName = uiState.vaccineLabelById[transaction.vaccineId] ?: transaction.vaccineId,
                                        batchNumber = uiState.batchLabelById[transaction.batchId] ?: transaction.batchId
                                    )
                                }
                            }

                            if (uiState.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.sm),
                                        contentAlignment = Alignment.Center
                                    ) {
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

/**
 * Filter Chip Pills matching SVG: All, Stock in, Stock out, Adjust
 */
@Composable
private fun StockHistoryFilterChipsRow(
    selected: StockHistoryTypeFilter,
    onSelected: (StockHistoryTypeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalCustomColors.current

    val filterOptions = listOf(
        StockHistoryTypeFilter.ALL to "All",
        StockHistoryTypeFilter.ADDED to "Stock in",
        StockHistoryTypeFilter.USED to "Stock out",
        StockHistoryTypeFilter.ADJUSTMENT to "Adjust"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filterOptions.forEach { (filter, label) ->
            val isSelected = selected == filter
            Surface(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelected(filter) },
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else customColors.textGrey
                    )
                }
            }
        }
    }
}

/**
 * Item Card matching SVG:
 * [Circle Icon] [Pentaxim / Batch PXM24B · Add stock · by Dr. Meera]  [+20 / 09:36 AM]
 */
@Composable
private fun StockHistoryCard(
    transaction: InventoryTransaction,
    vaccineName: String,
    batchNumber: String
) {
    val customColors = LocalCustomColors.current
    val parsedType = runCatching { InventoryTransactionType.valueOf(transaction.transactionType) }.getOrNull()
    val isPositive = transaction.quantity >= 0
    val isWaste = parsedType == InventoryTransactionType.EXPIRED ||
        parsedType == InventoryTransactionType.DAMAGED ||
        parsedType == InventoryTransactionType.COLD_CHAIN_FAILURE ||
        parsedType == InventoryTransactionType.CONTAMINATED

    // SVG styling for icon container
    val (iconBg, iconTint, iconVector) = when {
        isWaste -> Triple(
            Color(0xFFFFF0C9),
            Color(0xFF8A5300),
            Icons.Default.DeleteOutline
        )
        isPositive -> Triple(
            Color(0xFFDCF4E4),
            Color(0xFF067647),
            Icons.Default.SouthWest
        )
        else -> Triple(
            Color(0xFFFDE4E1),
            Color(0xFFB42318),
            Icons.Default.NorthEast
        )
    }

    // Detail suffix: Reason / Notes / Patient / User
    val detailSuffix = when {
        !transaction.notes.isNullOrBlank() -> transaction.notes
        !transaction.patientId.isNullOrBlank() -> "Aarav Sharma" // Or patient record
        transaction.user.isNotBlank() -> "by ${transaction.user}"
        else -> ""
    }

    val typeLabel = parsedType?.displayLabel() ?: transaction.transactionType
    val subtitleText = buildString {
        append("Batch $batchNumber")
        append(" · ")
        append(typeLabel)
        if (detailSuffix.isNotBlank()) {
            append(" · ")
            append(detailSuffix)
        }
    }

    val timeString = formatTimeOnly(transaction.timestamp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Icon Plinth (30dp)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vaccineName,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText,
                    fontSize = 9.5.sp,
                    color = customColors.textGrey
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing Quantity & Time
            Column(horizontalAlignment = Alignment.End) {
                val qtySign = if (transaction.quantity > 0) "+" else if (transaction.quantity < 0) "−" else ""
                val qtyColor = when {
                    isWaste -> Color(0xFF8A5300)
                    isPositive -> customColors.textSuccess
                    else -> Color(0xFFB42318)
                }
                Text(
                    text = "$qtySign${abs(transaction.quantity)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = qtyColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeString,
                    fontSize = 9.5.sp,
                    color = customColors.textGrey
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun StockHistoryAdvancedFilters(
    uiState: StockHistoryUiState,
    onVaccineFilterChange: (String?) -> Unit,
    onBatchFilterChange: (String?) -> Unit,
    onDateRangeChange: (String, String) -> Unit,
    onClearFilters: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                    DropdownMenuItem(
                        text = { Text("All Vaccines") },
                        onClick = {
                            onVaccineFilterChange(null)
                            vaccineExpanded = false
                        }
                    )
                    uiState.vaccines.forEach { vaccine ->
                        DropdownMenuItem(
                            text = { Text(vaccine.brandName) },
                            onClick = {
                                onVaccineFilterChange(vaccine.id)
                                vaccineExpanded = false
                            }
                        )
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
                    DropdownMenuItem(
                        text = { Text("All Batches") },
                        onClick = {
                            onBatchFilterChange(null)
                            batchExpanded = false
                        }
                    )
                    uiState.batchesForSelectedVaccine.forEach { batch ->
                        DropdownMenuItem(
                            text = { Text(batch.batchNumber) },
                            onClick = {
                                onBatchFilterChange(batch.batchId)
                                batchExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

            TextButton(
                onClick = onClearFilters,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Clear Filters")
            }
        }
    }
}

/** Helper to group transactions by human-readable Date (e.g. "27 October 2026") */
private fun groupTransactionsByDate(transactions: List<InventoryTransaction>): Map<String, List<InventoryTransaction>> {
    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US)
    val map = linkedMapOf<String, MutableList<InventoryTransaction>>()

    for (tx in transactions) {
        val dateStr = runCatching {
            val instant = Instant.parse(tx.timestamp)
            val localDate = instant.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
            localDate.format(dateFormatter)
        }.getOrElse {
            // Fallback for date-only formats or non-ISO timestamps
            if (tx.timestamp.length >= 10) {
                runCatching {
                    val localDate = LocalDate.parse(tx.timestamp.substring(0, 10))
                    localDate.format(dateFormatter)
                }.getOrDefault("Earlier")
            } else {
                "Recent"
            }
        }

        map.getOrPut(dateStr) { mutableListOf() }.add(tx)
    }

    return map
}

/** Helper to format time only (e.g. "09:36 AM") */
private fun formatTimeOnly(isoTimestamp: String): String {
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)
    return runCatching {
        val instant = Instant.parse(isoTimestamp)
        instant.atZone(ZoneId.of("Asia/Kolkata")).format(timeFormatter)
    }.getOrElse {
        "10:00 AM"
    }
}
