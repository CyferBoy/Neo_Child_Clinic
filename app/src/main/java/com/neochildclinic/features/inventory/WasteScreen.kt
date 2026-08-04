package com.neochildclinic.features.inventory

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.WasteRecord
import com.neochildclinic.core.ui.*
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay
import java.text.SimpleDateFormat
import java.util.*

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
    var editingRecord by remember { mutableStateOf<WasteRecord?>(null) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<WasteRecord?>(null) }
    
    val context = LocalContext.current

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    WasteContent(
        uiState = uiState,
        onBack = onBack,
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
                        Toast.makeText(context, "Waste updated", Toast.LENGTH_SHORT).show()
                        editingRecord = null
                    }
                } else {
                    viewModel.recordWaste(vaccineId, batchId, brand, batchNum, exp, date, reason, qty) {
                        Toast.makeText(context, "Waste recorded", Toast.LENGTH_SHORT).show()
                        showAddDialog = false
                    }
                }
            }
        )
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete this waste record? The stock (${record.quantity}) will be restored to batch ${record.batchNumber}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteWaste(record.id)
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
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
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (WasteRecord) -> Unit,
    onDeleteRequest: (WasteRecord) -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Waste Records") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
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
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                if (uiState.isLoading && uiState.wasteRecords.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.wasteRecords.isEmpty()) {
                    Text("No waste records found", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 88.dp, top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.wasteRecords, key = { it.id }) { record ->
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

@Composable
private fun WasteItemCard(
    record: WasteRecord, 
    onEdit: () -> Unit,
    onDelete: () -> Unit, 
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(record.brandName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = "${record.quantity} doses",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Text("Batch: ${record.batchNumber}", style = MaterialTheme.typography.bodySmall)
                Text("Reason: ${record.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val dateDisplay = remember(record.dateWasted) { formatDateForDisplay(record.dateWasted) }
                Text("Date: $dateDisplay", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
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
    
    val today = remember { SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date()) }
    var dateWasted by rememberSaveable { mutableStateOf(record?.dateWasted ?: today) }
    var reason by rememberSaveable { mutableStateOf(record?.reason ?: WASTE_REASONS[0]) }
    var quantityStr by rememberSaveable { mutableStateOf(record?.quantity?.toString() ?: "1") }
    
    var expandedBrand by rememberSaveable { mutableStateOf(false) }
    var expandedReason by rememberSaveable { mutableStateOf(false) }

    val filteredInventory = remember(brandSearch, inventory) {
        if (brandSearch.isEmpty()) inventory
        else inventory.filter { 
            it.brandName.contains(brandSearch, ignoreCase = true) ||
            it.batchNumber.contains(brandSearch, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (record == null) "Record New Waste" else "Edit Waste Record") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StandardAutoCompleteField(
                    value = if (selectedBatchId.isNotEmpty() && record != null) "$brandName ($batchNumber)" else brandSearch,
                    onValueChange = { 
                        brandSearch = it
                        expandedBrand = true 
                    },
                    label = "Select Vaccine Batch*",
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
                                        Text("Batch: ${item.batchNumber} | Stock: ${item.remainingQuantity}", style = MaterialTheme.typography.bodySmall)
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
                    val item = inventory.find { it.batchId == selectedBatchId }
                    Text(
                        "Available Stock: ${item?.remainingQuantity ?: 0} doses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                StandardTextField(
                    value = quantityStr,
                    onValueChange = { if (it.all { char -> char.isDigit() }) quantityStr = it },
                    label = "Quantity (Doses)*",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                DateDropdownPicker(
                    label = "Date Wasted*",
                    currentDate = dateWasted,
                    onDateSelected = { dateWasted = it },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedReason,
                    onExpandedChange = { expandedReason = it }
                ) {
                    StandardTextField(
                        value = reason,
                        onValueChange = {},
                        label = "Reason*",
                        readOnly = true,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedReason) }
                    )
                    ExposedDropdownMenu(
                        expanded = expandedReason,
                        onDismissRequest = { expandedReason = false }
                    ) {
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
                }
            }
        },
        confirmButton = {
            StandardButton(
                onClick = { 
                    val qty = quantityStr.toIntOrNull() ?: 0
                    onSave(selectedVaccineId, selectedBatchId, brandName, batchNumber, expiryDate, dateWasted, reason, qty)
                },
                enabled = selectedBatchId.isNotEmpty() && quantityStr.isNotEmpty() && !isSaving,
                isLoading = isSaving
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun WastePreview() {
    NeoChildTheme {
        WasteContent(
            uiState = WasteUiState(
                isLoading = false,
                wasteRecords = listOf(WasteRecord("1", "v1", "b1", "BCG", "B123", "2025-01-01", "2024-01-01", "Expired", 1))
            ),
            onBack = {},
            onAddClick = {},
            onEditClick = {},
            onDeleteRequest = {}
        )
    }
}
