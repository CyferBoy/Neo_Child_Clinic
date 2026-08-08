package com.neochildclinic.features.vaccination

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.*
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.UserRole
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaccinationScreen(
    patientId: String? = null,
    vaccinationId: String? = null,
    initialVaccineName: String? = null,
    onBack: () -> Unit,
    viewModel: AddVaccinationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(patientId) {
        patientId?.let { viewModel.loadPatient(it) }
    }

    LaunchedEffect(vaccinationId) {
        viewModel.loadVaccination(vaccinationId)
    }

    LaunchedEffect(initialVaccineName) {
        viewModel.setInitialVaccine(initialVaccineName)
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onBack()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Add Vaccination") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    PaddingValues(16.dp).let {
                        StandardButton(
                            onClick = { viewModel.saveVaccination() },
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            isLoading = uiState.isLoading
                        ) {
                            Text("Save Vaccination", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
            ) {
                // 1. Patient Summary
                uiState.patient?.let { patient ->
                    item { PatientSummaryCard(patient) }
                }

                // 2. Given Date
                item {
                    DateDropdownPicker(
                        label = "Given Date*",
                        currentDate = uiState.givenDate,
                        onDateSelected = { viewModel.updateGivenDate(it) }
                    )
                }

                // 2.5 Doctor Selection
                item {
                    DoctorDropdown(
                        doctors = uiState.allDoctors,
                        selectedDoctor = uiState.selectedDoctor,
                        onDoctorSelected = { viewModel.selectDoctor(it) },
                        isError = uiState.doctorError
                    )
                }

                // 3. Vaccines Section
                item { SectionHeader("Vaccines Administered") }
                
                items(uiState.vaccinesGiven, key = { it.id }) { row ->
                    VaccineRow(
                        state = row,
                        inventory = uiState.inventory,
                        onVaccineSelected = { viewModel.selectVaccine(row.id, it) },
                        onBatchSelected = { viewModel.selectBatch(row.id, it) },
                        onRemove = { viewModel.removeVaccineRow(row.id) },
                        isOnlyRow = uiState.vaccinesGiven.size == 1
                    )
                }

                item {
                    TextButton(onClick = { viewModel.addVaccineRow() }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Vaccine")
                    }
                }

                // 4. Payment Section
                item { SectionHeader("Payment Details") }
                item {
                    PaymentSection(
                        cash = uiState.cashAmount,
                        online = uiState.onlineAmount,
                        total = uiState.totalAmount,
                        onCashChange = { viewModel.updateCash(it) },
                        onOnlineChange = { viewModel.updateOnline(it) }
                    )
                }

                // 5. Next Vaccinations (Due Section)
                item { SectionHeader("Next Vaccination(s)") }
                items(uiState.followUps, key = { it.id }) { row ->
                    FollowUpRow(
                        state = row,
                        inventory = uiState.inventory,
                        availableTypes = uiState.availableDueTypes,
                        onTypeSelected = { type -> viewModel.updateFollowUpType(row.id, type) },
                        onVaccineToggled = { vaccine -> viewModel.toggleFollowUpVaccine(row.id, vaccine) },
                        onDueDateSelected = { date -> viewModel.updateFollowUpDueDate(row.id, date) },
                        onRemove = { viewModel.removeFollowUpRow(row.id) }
                    )
                }
                
                item {
                    TextButton(onClick = { viewModel.addFollowUpRow() }) {
                        Icon(Icons.Default.Event, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Next Vaccination")
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientSummaryCard(patient: Patient) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(patient.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val clinicId = if (patient.patientClinicId?.startsWith("TEMP-") == true || patient.patientClinicId.isNullOrBlank()) "Not Assigned" else patient.patientClinicId ?: "Not Assigned"
            Text("ID: $clinicId", style = MaterialTheme.typography.bodyMedium)
            Text("${patient.gender} | DOB: ${patient.dob}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccineRow(
    state: VaccineSelectionState,
    inventory: List<InventoryItem>,
    onVaccineSelected: (InventoryItem) -> Unit,
    onBatchSelected: (com.neochildclinic.data.local.entity.VaccineBatchEntity) -> Unit,
    onRemove: () -> Unit,
    isOnlyRow: Boolean
) {
    var vaccineSearch by remember { mutableStateOf(state.selectedVaccine?.brandName ?: "") }
    var vaccineExpanded by remember { mutableStateOf(false) }
    var batchExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vaccine Row", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (!isOnlyRow) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.Red)
                    }
                }
            }

            // Vaccine Selection
            StandardAutoCompleteField(
                value = vaccineSearch,
                onValueChange = { 
                    vaccineSearch = it
                    vaccineExpanded = true
                },
                label = "Vaccine*",
                expanded = vaccineExpanded,
                onExpandedChange = { vaccineExpanded = it },
                placeholder = "Search Vaccine"
            ) {
                val filtered = inventory.filter { it.brandName.contains(vaccineSearch, ignoreCase = true) }
                filtered.forEach { vaccine ->
                    DropdownMenuItem(
                        text = { Text("${vaccine.brandName} (${vaccine.stock} in stock)") },
                        onClick = {
                            onVaccineSelected(vaccine)
                            vaccineSearch = vaccine.brandName
                            vaccineExpanded = false
                        }
                    )
                }
            }

            // Batch Selection
            val batches = state.selectedVaccine?.batches?.filter { it.remainingQuantity > 0 } ?: emptyList()
            
            ExposedDropdownMenuBox(
                expanded = batchExpanded,
                onExpandedChange = { if (batches.isNotEmpty()) batchExpanded = it }
            ) {
                StandardTextField(
                    value = state.selectedBatch?.let { "${it.batchNumber} (Qty: ${it.remainingQuantity})" } ?: "Select Batch",
                    onValueChange = {},
                    readOnly = true,
                    label = "Batch*",
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = batchExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = batchExpanded,
                    onDismissRequest = { batchExpanded = false }
                ) {
                    batches.forEach { batch ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text(batch.batchNumber, fontWeight = FontWeight.Bold)
                                    Text("Qty: ${batch.remainingQuantity} | Exp: ${batch.expiryDate}", fontSize = 12.sp)
                                }
                            },
                            onClick = {
                                onBatchSelected(batch)
                                batchExpanded = false
                            }
                        )
                    }
                }
            }

            if (state.selectedBatch != null) {
                Text(
                    "Expiry: ${state.selectedBatch.expiryDate}", 
                    style = MaterialTheme.typography.labelSmall,
                    color = if (com.neochildclinic.core.utils.InventoryUtils.isExpired(state.selectedBatch.expiryDate)) Color.Red else Color.Gray
                )
            }
        }
    }
}

@Composable
private fun PaymentSection(
    cash: String,
    online: String,
    total: Double,
    onCashChange: (String) -> Unit,
    onOnlineChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StandardTextField(
                    value = cash,
                    onValueChange = onCashChange,
                    label = "Cash Amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                StandardTextField(
                    value = online,
                    onValueChange = onOnlineChange,
                    label = "Online Amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total Amount", style = MaterialTheme.typography.titleMedium)
                Text("₹$total", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FollowUpRow(
    state: FollowUpSelectionState,
    inventory: List<InventoryItem>,
    availableTypes: List<String>,
    onTypeSelected: (String) -> Unit,
    onVaccineToggled: (InventoryItem) -> Unit,
    onDueDateSelected: (String) -> Unit,
    onRemove: () -> Unit
) {
    var vaccineSearch by remember { mutableStateOf("") }
    var vaccineExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Next Vaccination", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = Color.Gray)
                }
            }

            // Type -- mandatory
            DueVaccinationTypeDropdown(
                types = availableTypes,
                selectedType = state.type,
                onTypeSelected = onTypeSelected,
                label = "Type*",
                isError = state.typeError
            )

            // Vaccine -- optional, multi-select
            StandardAutoCompleteField(
                value = vaccineSearch,
                onValueChange = { 
                    vaccineSearch = it
                    vaccineExpanded = true
                },
                label = "Vaccine (Optional)",
                expanded = vaccineExpanded,
                onExpandedChange = { vaccineExpanded = it },
                placeholder = "Search vaccine"
            ) {
                val filtered = inventory.filter { it.brandName.contains(vaccineSearch, ignoreCase = true) }
                filtered.forEach { vaccine ->
                    val isSelected = state.nextVaccines.any { it.id == vaccine.id }
                    DropdownMenuItem(
                        text = { Text(vaccine.brandName) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        onClick = {
                            onVaccineToggled(vaccine)
                            vaccineSearch = ""
                        }
                    )
                }
            }

            if (state.nextVaccines.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.nextVaccines.forEach { vaccine ->
                        InputChip(
                            selected = true,
                            onClick = { onVaccineToggled(vaccine) },
                            label = { Text(vaccine.brandName) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            // Due Date
            DateDropdownPicker(
                label = "Due Date*",
                currentDate = state.dueDate,
                onDateSelected = onDueDateSelected
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.primary
    )
}
