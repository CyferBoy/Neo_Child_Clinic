package com.neochildclinic.feature.waste.presentation

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.common.Constants
import com.neochildclinic.core.common.PatientUtils.formatDateForDisplay
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.*
import com.neochildclinic.domain.model.WasteRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.coroutines.launch

private val WASTE_REASONS = listOf(
    "Expired",
    "Broken vial",
    "Cold chain failure",
    "Contaminated",
    "Returned",
    "Other"
)

@Composable
fun WasteScreen(
    onBack: () -> Unit,
    viewModel: WasteViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editingRecord by remember { mutableStateOf<WasteRecord?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<WasteRecord?>(null) }

    ShowSnackbar(uiState.error, snackbarHostState, duration = SnackbarDuration.Short) { viewModel.clearError() }

    WasteContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onAddClick = { showAddDialog = true },
        onEditClick = { editingRecord = it },
        onDeleteRequest = { recordToDelete = it }
    )

    if (showAddDialog || editingRecord != null) {
        WasteEntryDialog(
            record = editingRecord,
            inventory = uiState.inventory,
            isSaving = uiState.isSaving,
            onDismiss = { 
                showAddDialog = false
                editingRecord = null
            },
            onSave = { vaccineId, batchId, brand, batchNum, exp, date, reason, qty ->
                if (editingRecord != null) {
                    viewModel.updateWaste(editingRecord!!.id, vaccineId, batchId, brand, batchNum, exp, date, reason, qty) {
                        scope.launch { snackbarHostState.showSnackbar("Waste updated") }
                        editingRecord = null
                    }
                } else {
                    viewModel.recordWaste(vaccineId, batchId, brand, batchNum, exp, date, reason, qty) {
                        scope.launch { snackbarHostState.showSnackbar("Waste recorded") }
                        showAddDialog = false
                    }
                }
            }
        )
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Confirm Deletion", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this waste record? The stock (${record.quantity}) will be restored to batch ${record.batchNumber}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWaste(record.id)
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WasteContent(
    uiState: WasteUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (WasteRecord) -> Unit,
    onDeleteRequest: (WasteRecord) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Expired", "Broken vial", "Cold chain failure")

    val filteredRecords = remember(uiState.wasteRecords, selectedFilter) {
        if (selectedFilter == "All") uiState.wasteRecords
        else uiState.wasteRecords.filter { it.reason.equals(selectedFilter, ignoreCase = true) }
    }

    val totalWastedDoses = remember(uiState.wasteRecords) {
        uiState.wasteRecords.sumOf { it.quantity }
    }

    AppBackground {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("Waste Records") },
                    onBack = onBack
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Waste")
                }
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(paddingValues).fillMaxSize()
            ) {
                if (uiState.isLoading && uiState.wasteRecords.isEmpty()) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 8,
                        cardShaped = true,
                        contentPadding = PaddingValues(Spacing.screen)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
                        contentPadding = PaddingValues(bottom = 100.dp, top = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // 1. Hero Waste Summary Banner
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(Spacing.card),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$totalWastedDoses doses wasted",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "${uiState.wasteRecords.size} total incident logs",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }

                        // 2. Filter Pills Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                filters.forEach { filter ->
                                    val isSelected = selectedFilter == filter
                                    Surface(
                                        onClick = { selectedFilter = filter },
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

                        // 3. Eyebrow Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${filteredRecords.size} records",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = onAddClick) {
                                    Text(
                                        text = "Record waste",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // 4. Waste Item Cards
                        if (filteredRecords.isEmpty()) {
                            item {
                                EmptyState(
                                    icon = Icons.Default.DeleteSweep,
                                    title = "No waste records",
                                    message = "No recorded waste found for the selected filter."
                                )
                            }
                        } else {
                            items(filteredRecords, key = { it.id }) { record ->
                                WasteItemCard(
                                    record = record, 
                                    onEdit = { onEditClick(record) },
                                    onDelete = { onDeleteRequest(record) }, 
                                    modifier = Modifier.animateItem()
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
private fun WasteItemCard(
    record: WasteRecord, 
    onEdit: () -> Unit,
    onDelete: () -> Unit, 
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.card),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.brandName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Batch ${record.batchNumber} · ${record.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val dateDisplay = remember(record.dateWasted) { formatDateForDisplay(record.dateWasted) }
                Text(
                    text = "Recorded $dateDisplay",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${record.quantity}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "doses",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WasteEntryDialog(
    record: WasteRecord? = null,
    inventory: List<WasteInventoryItem>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, Int) -> Unit
) {
    var brandSearch by rememberSaveable { mutableStateOf(record?.brandName ?: "") }
    var selectedVaccineId by rememberSaveable { mutableStateOf(record?.vaccineId ?: "") }
    var selectedBatchId by rememberSaveable { mutableStateOf(record?.batchId ?: "") }
    var brandName by rememberSaveable { mutableStateOf(record?.brandName ?: "") }
    var batchNumber by rememberSaveable { mutableStateOf(record?.batchNumber ?: "") }
    var expiryDate by rememberSaveable { mutableStateOf(record?.expiryDate ?: "") }

    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern(Constants.DATE_FORMAT, Locale.ENGLISH)) }
    var dateWasted by rememberSaveable { mutableStateOf(record?.dateWasted ?: today) }
    var reason by rememberSaveable { mutableStateOf(record?.reason ?: WASTE_REASONS[0]) }
    var quantityText by rememberSaveable { mutableStateOf(record?.quantity?.toString() ?: "1") }

    var expandedBrand by rememberSaveable { mutableStateOf(false) }
    var expandedReason by rememberSaveable { mutableStateOf(false) }

    val filteredInventory = remember(brandSearch, inventory) {
        if (brandSearch.isEmpty()) inventory
        else inventory.filter {
            it.brandName.contains(brandSearch, ignoreCase = true) ||
            it.batchNumber.contains(brandSearch, ignoreCase = true)
        }
    }

    val selectedItem = remember(selectedBatchId, inventory) {
        inventory.find { it.batchId == selectedBatchId }
    }
    val maxQuantity = remember(selectedItem, record) {
        val remaining = selectedItem?.remainingQuantity ?: 0
        if (record != null && record.batchId == selectedBatchId) remaining + record.quantity else remaining
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                if (record != null) "Edit Waste Record" else "Record Waste",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Vaccine and Batch selection
                StandardAutoCompleteField(
                    value = if (selectedBatchId.isNotEmpty() && record != null) "$brandName ($batchNumber)" else brandSearch,
                    onValueChange = {
                        brandSearch = it
                        expandedBrand = true
                    },
                    label = "Select Vaccine Batch *",
                    placeholder = "Search brand or batch...",
                    expanded = expandedBrand && filteredInventory.isNotEmpty(),
                    onExpandedChange = { expandedBrand = it },
                    enabled = record == null,
                    dropdownContent = {
                        filteredInventory.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.brandName, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Batch: ${item.batchNumber} | Stock: ${item.remainingQuantity}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                onClick = {
                                    brandSearch = item.brandName
                                    brandName = item.brandName
                                    selectedVaccineId = item.vaccineId
                                    selectedBatchId = item.batchId
                                    batchNumber = item.batchNumber
                                    expiryDate = item.expiryDate
                                    expandedBrand = false
                                }
                            )
                        }
                    }
                )

                if (selectedBatchId.isNotEmpty()) {
                    Text(
                        "Available Stock: ${selectedItem?.remainingQuantity ?: 0} doses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Reason selection
                StandardAutoCompleteField(
                    value = reason,
                    onValueChange = {},
                    label = "Reason *",
                    expanded = expandedReason,
                    onExpandedChange = { expandedReason = it },
                    dropdownContent = {
                        WASTE_REASONS.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r) },
                                onClick = {
                                    reason = r
                                    expandedReason = false
                                }
                            )
                        }
                    }
                )

                // Quantity
                StandardTextField(
                    value = quantityText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) quantityText = it },
                    label = "Doses to Waste *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = if (maxQuantity > 0) "Max $maxQuantity" else "Enter doses",
                    modifier = Modifier.fillMaxWidth()
                )

                DateDropdownPicker(
                    label = "Date Wasted *",
                    currentDate = dateWasted,
                    onDateSelected = { dateWasted = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            StandardButton(
                onClick = {
                    val qty = quantityText.toIntOrNull() ?: 0
                    if (selectedVaccineId.isNotBlank() && selectedBatchId.isNotBlank() && qty > 0) {
                        onSave(selectedVaccineId, selectedBatchId, brandName, batchNumber, expiryDate, dateWasted, reason, qty)
                    }
                },
                isLoading = isSaving,
                enabled = selectedVaccineId.isNotBlank() && selectedBatchId.isNotBlank() && (quantityText.toIntOrNull() ?: 0) > 0 && !isSaving
            ) {
                Text(if (record != null) "Update" else "Record Waste")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
