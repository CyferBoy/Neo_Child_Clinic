package com.neochildclinic.feature.inventory.presentation

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.domain.model.InventoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStockScreen(
    onBack: () -> Unit = {},
    viewModel: AddStockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Stock saved", Toast.LENGTH_SHORT).show()
            viewModel.resetState()
            onBack()
        }
    }

    ShowSnackbar(uiState.error, snackbarHostState) { viewModel.clearError() }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                BackTopAppBar(
                    title = { Text("Add Stock") },
                    onBack = onBack
                )
            }
        ) { padding ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            val allBatches = remember(uiState.vaccineSections) {
                uiState.vaccineSections.flatMap { it.batches }
            }
            val totalDoses = remember(allBatches) {
                allBatches.sumOf { it.quantity.toIntOrNull() ?: 0 }
            }
            val totalBatchesCount = remember(allBatches) {
                allBatches.count { it.batchNumber.isNotBlank() || it.quantity.isNotBlank() }.coerceAtLeast(allBatches.size)
            }
            val totalNetValue = remember(allBatches) {
                allBatches.sumOf {
                    val q = it.quantity.toIntOrNull() ?: 0
                    val nr = it.netRate.toDoubleOrNull() ?: 0.0
                    q * nr
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = "Add stock for one or more vaccines, each with one or more batches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val selectedVaccineIds = remember(uiState.vaccineSections) {
                    uiState.vaccineSections.map { it.vaccineId }.filter { it.isNotBlank() }.toSet()
                }

                uiState.vaccineSections.forEachIndexed { index, section ->
                    VaccineStockSection(
                        section = section,
                        index = index,
                        availableVaccines = uiState.availableVaccines,
                        alreadySelectedIds = selectedVaccineIds,
                        canRemoveSection = uiState.vaccineSections.size > 1,
                        onVaccineSelected = { vaccine -> viewModel.selectVaccine(section.localId, vaccine) },
                        onRemoveSection = { viewModel.removeVaccineSection(section.localId) },
                        onAddBatch = { viewModel.addBatchRow(section.localId) },
                        onRemoveBatch = { batchId -> viewModel.removeBatchRow(section.localId, batchId) },
                        onUpdateBatch = { batchId, update -> viewModel.updateBatch(section.localId, batchId, update) }
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.addVaccineSection() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Vaccine")
                }

                Text(
                    text = "Delivery summary",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = Spacing.xs)
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF0ECE3),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.card).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$totalDoses doses · $totalBatchesCount batch${if (totalBatchesCount == 1) "" else "es"}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF14201F)
                            )
                            Text(
                                text = "Stock value ₹${String.format(java.util.Locale.ENGLISH, "%,.0f", totalNetValue)} at net rate",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5F6D68)
                            )
                        }
                        Text(
                            text = "₹${String.format(java.util.Locale.ENGLISH, "%,.0f", totalNetValue)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF14201F)
                        )
                    }
                }

                Surface(
                    color = Color(0xFFFFF0C9),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = Color(0xFF563400),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Check expiry before saving",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF563400)
                            )
                            Text(
                                text = "Expired batches are hidden from vaccination.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF563400).copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xs))

                StandardButton(
                    onClick = { viewModel.submit() },
                    isLoading = uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Save Stock")
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccineStockSection(
    section: StockVaccineFormState,
    index: Int,
    availableVaccines: List<InventoryItem>,
    alreadySelectedIds: Set<String>,
    canRemoveSection: Boolean,
    onVaccineSelected: (InventoryItem) -> Unit,
    onRemoveSection: () -> Unit,
    onAddBatch: () -> Unit,
    onRemoveBatch: (String) -> Unit,
    onUpdateBatch: (String, (StockBatchFormState) -> StockBatchFormState) -> Unit
) {
    var isChangingVaccine by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (section.vaccineId.isNotBlank() && !isChangingVaccine) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFDCE9F8),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.card).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vaccine",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF0A2F52).copy(alpha = 0.7f)
                        )
                        Text(
                            text = section.brandName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF0A2F52)
                        )
                        Text(
                            text = section.companyName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF0A2F52).copy(alpha = 0.8f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { isChangingVaccine = true }) {
                            Text("Change", color = Color(0xFF0A2F52), fontWeight = FontWeight.SemiBold)
                        }
                        if (canRemoveSection) {
                            IconButton(onClick = onRemoveSection) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFB42318))
                            }
                        }
                    }
                }
            }
        } else {
            VaccinePickerDropdown(
                selected = section,
                availableVaccines = availableVaccines,
                alreadySelectedIds = alreadySelectedIds,
                onVaccineSelected = {
                    onVaccineSelected(it)
                    isChangingVaccine = false
                }
            )
        }

        if (section.vaccineId.isNotBlank()) {
            Text(
                text = "Stock received",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Add one or more batches from this delivery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            section.batches.forEachIndexed { batchIndex, batch ->
                BatchFormRow(
                    batch = batch,
                    batchIndex = batchIndex,
                    canRemove = section.batches.size > 1,
                    onRemove = { onRemoveBatch(batch.localId) },
                    onChange = { update -> onUpdateBatch(batch.localId, update) }
                )
            }

            OutlinedButton(
                onClick = onAddBatch,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Add another batch")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccinePickerDropdown(
    selected: StockVaccineFormState,
    availableVaccines: List<InventoryItem>,
    alreadySelectedIds: Set<String>,
    onVaccineSelected: (InventoryItem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = if (selected.vaccineId.isBlank()) "" else "${selected.brandName} (${selected.companyName})",
            onValueChange = {},
            readOnly = true,
            label = { Text("Select Vaccine*") },
            placeholder = { Text("Choose a vaccine") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (availableVaccines.isEmpty()) {
                DropdownMenuItem(text = { Text("No vaccines found. Add a vaccine type first.") }, onClick = {}, enabled = false)
            }
            availableVaccines.forEach { vaccine ->
                val alreadyUsedElsewhere = vaccine.id in alreadySelectedIds && vaccine.id != selected.vaccineId
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(vaccine.brandName)
                            Text(
                                if (alreadyUsedElsewhere) "${vaccine.company} \u2022 already added above" else vaccine.company,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (alreadyUsedElsewhere) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    enabled = !alreadyUsedElsewhere,
                    onClick = {
                        onVaccineSelected(vaccine)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun BatchFormRow(
    batch: StockBatchFormState,
    batchIndex: Int,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onChange: ((StockBatchFormState) -> StockBatchFormState) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Batch ${batchIndex + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (canRemove) {
                    TextButton(
                        onClick = onRemove,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "Remove",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFB42318)
                        )
                    }
                }
            }

            StandardTextField(
                value = batch.batchNumber,
                onValueChange = { value -> onChange { it.copy(batchNumber = value) } },
                label = "Batch number *",
                placeholder = "e.g. PXM24C"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom
            ) {
                DateDropdownPicker(
                    label = "Expiry date *",
                    currentDate = batch.expiryDate,
                    onDateSelected = { value -> onChange { it.copy(expiryDate = value) } },
                    modifier = Modifier.weight(1f)
                )

                StandardTextField(
                    value = batch.quantity,
                    onValueChange = { value -> if (value.isEmpty() || value.toIntOrNull() != null) onChange { it.copy(quantity = value) } },
                    label = "Quantity *",
                    placeholder = "0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StandardTextField(
                    value = batch.mrp,
                    onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull() != null) onChange { it.copy(mrp = value) } },
                    label = "MRP *",
                    placeholder = "₹0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                StandardTextField(
                    value = batch.netRate,
                    onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull() != null) onChange { it.copy(netRate = value) } },
                    label = "Net rate *",
                    placeholder = "₹0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            StandardTextField(
                value = batch.manufacturer,
                onValueChange = { value -> onChange { it.copy(manufacturer = value) } },
                label = "Batch manufacturer (if different)",
                placeholder = "Optional"
            )
        }
    }
}
