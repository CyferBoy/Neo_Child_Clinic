package com.neochildclinic.features.inventory

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.DateDropdownPicker
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

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Stock saved", Toast.LENGTH_SHORT).show()
            viewModel.resetState()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Add Stock") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Add stock for one or more vaccines, each with one or more batches.",
                    style = MaterialTheme.typography.bodyMedium,
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Vaccine")
                }

                Spacer(Modifier.height(8.dp))

                StandardButton(
                    onClick = { viewModel.submit() },
                    isLoading = uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Stock")
                }

                Spacer(Modifier.height(16.dp))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Vaccine ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (canRemoveSection) {
                    TextButton(onClick = onRemoveSection) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Remove Vaccine")
                    }
                }
            }

            VaccinePickerDropdown(
                selected = section,
                availableVaccines = availableVaccines,
                alreadySelectedIds = alreadySelectedIds,
                onVaccineSelected = onVaccineSelected
            )

            if (section.vaccineId.isNotBlank()) {
                HorizontalDivider()

                section.batches.forEachIndexed { batchIndex, batch ->
                    BatchFormRow(
                        batch = batch,
                        batchIndex = batchIndex,
                        canRemove = section.batches.size > 1,
                        onRemove = { onRemoveBatch(batch.localId) },
                        onChange = { update -> onUpdateBatch(batch.localId, update) }
                    )
                    if (batchIndex != section.batches.lastIndex) HorizontalDivider()
                }

                TextButton(onClick = onAddBatch) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Batch")
                }
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
                // A vaccine already chosen in another section is still shown, but disabled -
                // this is the UI-level guard against accidentally adding it twice; the
                // repository also enforces this server-side as a second line of defense.
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Batch ${batchIndex + 1}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove Batch", modifier = Modifier.size(18.dp))
                }
            }
        }

        StandardTextField(
            value = batch.batchNumber,
            onValueChange = { value -> onChange { it.copy(batchNumber = value) } },
            label = "Batch Number*"
        )

        StandardTextField(
            value = batch.quantity,
            onValueChange = { value -> if (value.isEmpty() || value.toIntOrNull() != null) onChange { it.copy(quantity = value) } },
            label = "Quantity*",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        DateDropdownPicker(
            label = "Expiry Date*",
            currentDate = batch.expiryDate,
            onDateSelected = { value -> onChange { it.copy(expiryDate = value) } },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StandardTextField(
                value = batch.mrp,
                onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull() != null) onChange { it.copy(mrp = value) } },
                label = "MRP*",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            StandardTextField(
                value = batch.netRate,
                onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull() != null) onChange { it.copy(netRate = value) } },
                label = "Net Rate*",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }

        StandardTextField(
            value = batch.manufacturer,
            onValueChange = { value -> onChange { it.copy(manufacturer = value) } },
            label = "Batch Manufacturer (if different)",
            placeholder = "e.g. Sanofi"
        )
    }
}
