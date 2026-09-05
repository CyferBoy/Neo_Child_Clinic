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
import androidx.compose.runtime.saveable.rememberSaveable
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
    val isEdit = vaccinationId != null
    var editGivenDate by rememberSaveable { mutableStateOf(false) }
    var editDoctor by rememberSaveable { mutableStateOf(false) }
    var editVaccineBatch by rememberSaveable { mutableStateOf(false) }
    var editQuantity by rememberSaveable { mutableStateOf(false) }
    var editPayment by rememberSaveable { mutableStateOf(false) }
    var editNextVaccination by rememberSaveable { mutableStateOf(false) }

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
                    title = { Text(if (isEdit) "Edit Vaccination" else "Add Vaccination") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
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
                            onClick = { viewModel.saveVaccination(editVaccineBatch = editVaccineBatch, editQuantity = editQuantity) },
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            enabled = !isEdit || !uiState.isVaccinationLoading,
                            isLoading = uiState.isLoading
                        ) {
                            Text(
                                if (isEdit && uiState.isVaccinationLoading) "Loading..."
                                else if (isEdit) "Save Changes" else "Save Vaccination",
                                style = MaterialTheme.typography.titleMedium
                            )
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
                    EditSectionHeader(
                        title = "Given Date",
                        checked = if (isEdit) editGivenDate else true,
                        enabled = isEdit,
                        onCheckedChange = { editGivenDate = it }
                    )
                    if (!isEdit || editGivenDate) {
                        DateDropdownPicker(
                            label = "Given Date*",
                            currentDate = uiState.givenDate,
                            onDateSelected = { viewModel.updateGivenDate(it) }
                        )
                    } else {
                        ReadOnlyValue(uiState.givenDate)
                    }
                }

                // 2.5 Doctor Selection
                item {
                    EditSectionHeader(
                        title = "Doctor",
                        checked = if (isEdit) editDoctor else true,
                        enabled = isEdit,
                        onCheckedChange = { editDoctor = it }
                    )
                    if (!isEdit || editDoctor) {
                        DoctorDropdown(
                            doctors = uiState.allDoctors,
                            selectedDoctor = uiState.selectedDoctor,
                            onDoctorSelected = { viewModel.selectDoctor(it) },
                            isError = uiState.doctorError
                        )
                    } else {
                        ReadOnlyValue(uiState.selectedDoctor?.displayName ?: "Not selected")
                    }
                }

                // 3. Vaccine & Batch + Quantity
                item {
                    EditSectionHeader(
                        title = "Vaccine & Batch",
                        checked = if (isEdit) editVaccineBatch else true,
                        enabled = isEdit,
                        onCheckedChange = { editVaccineBatch = it }
                    )
                    if (isEdit && !editVaccineBatch && !editQuantity) {
                        ReadOnlyValue(uiState.vaccinesGiven.joinToString("\n") { row ->
                            val vaccine = row.selectedVaccine?.brandName ?: "Not selected"
                            val batch = row.selectedBatch?.batchNumber ?: "No batch"
                            "$vaccine • Batch: $batch • Quantity: ${row.quantity}"
                        })
                    }
                }
                if (isEdit) {
                    item {
                        EditSectionHeader(
                            title = "Quantity",
                            checked = editQuantity,
                            enabled = true,
                            onCheckedChange = { editQuantity = it }
                        )
                    }
                }
                if (!isEdit || editVaccineBatch || editQuantity) {
                    items(uiState.vaccinesGiven, key = { it.id }) { row ->
                        VaccineRow(
                            state = row,
                            inventory = uiState.inventory,
                            givenDate = uiState.givenDate,
                            onVaccineSelected = { viewModel.selectVaccine(row.id, it) },
                            onBatchSelected = { viewModel.selectBatch(row.id, it) },
                            onQuantityChange = { viewModel.updateQuantity(row.id, it) },
                            allowVaccineBatchEdit = !isEdit || editVaccineBatch,
                            allowQuantityEdit = !isEdit || editQuantity,
                            onRemove = { viewModel.removeVaccineRow(row.id) },
                            isOnlyRow = uiState.vaccinesGiven.size == 1
                        )
                    }
                    item {
                        if (!isEdit || editVaccineBatch) {
                            TextButton(onClick = { viewModel.addVaccineRow() }) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Add Vaccine")
                            }
                        }
                    }
                }

                // 4. Payment Section
                item {
                    EditSectionHeader(
                        title = "Payment",
                        checked = if (isEdit) editPayment else true,
                        enabled = isEdit,
                        onCheckedChange = { editPayment = it }
                    )
                    if (!isEdit || editPayment) {
                        PaymentSection(
                            cash = uiState.cashAmount,
                            online = uiState.onlineAmount,
                            total = uiState.totalAmount,
                            withFees = uiState.withFees,
                            doctorsAcc = uiState.doctorsAcc,
                            onCashChange = { viewModel.updateCash(it) },
                            onOnlineChange = { viewModel.updateOnline(it) },
                            onFeesToggle = { viewModel.updateWithFees(it) },
                            onAccToggle = { viewModel.updateDoctorsAccount(it) }
                        )
                    } else {
                        ReadOnlyValue("Cash: ₹${uiState.cashAmount} | Online: ₹${uiState.onlineAmount}\nTotal: ₹${uiState.totalAmount}")
                    }
                }

                // 5. Next Vaccination Section
                item {
                    EditSectionHeader(
                        title = "Next Vaccination",
                        checked = if (isEdit) editNextVaccination else true,
                        enabled = isEdit,
                        onCheckedChange = { editNextVaccination = it }
                    )
                    if (isEdit && !editNextVaccination) {
                        ReadOnlyValue(if (uiState.nextVaccinations.isEmpty()) "No next vaccination" else uiState.nextVaccinations.joinToString("\n") { row ->
                            val vaccines = row.nextVaccines.joinToString(", ") { it.brandName }
                            if (vaccines.isNotBlank()) "$vaccines • Due: ${row.dueDate}" else "${row.type} • Due: ${row.dueDate}"
                        })
                    }
                }
                if (!isEdit || editNextVaccination) {
                    items(uiState.nextVaccinations.indices.toList(), key = { it }) { index ->
                        NextVaccinationSection(
                            index = index,
                            state = uiState.nextVaccinations[index],
                            inventory = uiState.inventory,
                            availableTypes = uiState.availableDueTypes,
                            canRemove = true,
                            onTypeSelected = { viewModel.updateNextVaccinationType(index, it) },
                            onVaccineToggled = { viewModel.toggleNextVaccinationVaccine(index, it) },
                            onDueDateSelected = { viewModel.updateNextVaccinationDueDate(index, it) },
                            onRemove = { viewModel.removeNextVaccination(index) },
                            onCancel = { viewModel.cancelNextVaccination(index) },
                            onVaccineCancel = { viewModel.cancelNextVaccinationVaccine(index, it) }
                        )
                    }
                    item {
                        OutlinedButton(onClick = { viewModel.addNextVaccination() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.nextVaccinations.isEmpty()) "Add Next Vaccination" else "Add Another Next Vaccination")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditSectionHeader(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (enabled) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (!enabled || !checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ReadOnlyValue(value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            value,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
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
    givenDate: String,
    onVaccineSelected: (InventoryItem) -> Unit,
    onBatchSelected: (com.neochildclinic.data.local.entity.VaccineBatchEntity) -> Unit,
    onQuantityChange: (String) -> Unit,
    allowVaccineBatchEdit: Boolean,
    allowQuantityEdit: Boolean,
    onRemove: () -> Unit,
    isOnlyRow: Boolean
) {
    var vaccineSearch by remember { mutableStateOf(state.selectedVaccine?.brandName ?: "") }
    var vaccineExpanded by remember { mutableStateOf(false) }
    var batchExpanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vaccine Row", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (!isOnlyRow && allowVaccineBatchEdit) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color.Red)
                    }
                }
            }

            if (allowVaccineBatchEdit) {
                StandardAutoCompleteField(
                    value = vaccineSearch,
                    onValueChange = { vaccineSearch = it; vaccineExpanded = true },
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
            } else {
                ReadOnlyValue("Vaccine: ${state.selectedVaccine?.brandName ?: "Not selected"}")
            }

            val batches = state.selectedVaccine?.batches?.filter {
                it.remainingQuantity > 0 && !com.neochildclinic.core.utils.InventoryUtils.isExpiredAsOf(it.expiryDate, givenDate)
            } ?: emptyList()
            if (allowVaccineBatchEdit) {
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
                                onClick = { onBatchSelected(batch); batchExpanded = false }
                            )
                        }
                    }
                }
            } else {
                ReadOnlyValue("Batch: ${state.selectedBatch?.batchNumber ?: "No batch"}")
            }

            if (state.selectedBatch != null) {
                Text(
                    "Expiry: ${state.selectedBatch.expiryDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (com.neochildclinic.core.utils.InventoryUtils.isExpiredAsOf(state.selectedBatch.expiryDate, givenDate)) Color.Red else Color.Gray
                )
            }

            StandardTextField(
                value = state.quantity.toString(),
                onValueChange = onQuantityChange,
                label = "Quantity*",
                readOnly = !allowQuantityEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

@Composable
private fun PaymentSection(
    cash: String,
    online: String,
    total: Double,
    withFees: Boolean,
    doctorsAcc: Boolean,
    onCashChange: (String) -> Unit,
    onOnlineChange: (String) -> Unit,
    onFeesToggle: (Boolean) -> Unit,
    onAccToggle: (Boolean) -> Unit
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
                Text(
                    "₹$total",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = withFees,
                        onCheckedChange = onFeesToggle
                    )
                    Text("With Fees")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = doctorsAcc,
                        onCheckedChange = onAccToggle
                    )
                    Text("Doctor's Account")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NextVaccinationSection(
    index: Int,
    state: NextVaccinationState,
    inventory: List<InventoryItem>,
    availableTypes: List<String>,
    canRemove: Boolean,
    onTypeSelected: (String) -> Unit,
    onVaccineToggled: (InventoryItem) -> Unit,
    onDueDateSelected: (String) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onVaccineCancel: (InventoryItem) -> Unit
) {
    var vaccineExpanded by remember { mutableStateOf(false) }
    var showCancelDialog by remember(state.reminderId) { mutableStateOf(false) }
    var vaccineToCancel by remember { mutableStateOf<InventoryItem?>(null) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Next Vaccination ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.reminderId != null) {
                    TextButton(onClick = { showCancelDialog = true }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.error)
                    }
                } else if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove next vaccination")
                    }
                }
            }

            DueVaccinationTypeDropdown(
                types = availableTypes,
                selectedType = state.type,
                onTypeSelected = onTypeSelected,
                label = "Type*",
                isError = state.typeError
            )

            // Vaccine picker for this section now mirrors the Batch field's UX in the
            // Vaccine & Batch section above: a plain tap-to-open dropdown listing the
            // available options, not a free-text search field. (Multiple vaccines can
            // still be picked - the dropdown just closes after each pick, same as Batch.)
            val filteredVaccines = if (state.type.isBlank()) {
                emptyList()
            } else {
                inventory.filter { it.type.equals(state.type, ignoreCase = true) }
            }
            ExposedDropdownMenuBox(
                expanded = vaccineExpanded,
                onExpandedChange = { if (state.type.isNotBlank()) vaccineExpanded = it }
            ) {
                StandardTextField(
                    value = if (state.type.isBlank()) "Select a Type first" else "Select Vaccine",
                    onValueChange = {},
                    readOnly = true,
                    label = "Vaccine (Optional)",
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vaccineExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = vaccineExpanded,
                    onDismissRequest = { vaccineExpanded = false }
                ) {
                    if (filteredVaccines.isEmpty()) {
                        DropdownMenuItem(text = { Text("No vaccines found for \"${state.type}\"") }, onClick = {}, enabled = false)
                    }
                    filteredVaccines.forEach { vaccine ->
                        val isSelected = state.nextVaccines.any { it.id == vaccine.id }
                        DropdownMenuItem(
                            text = { Text(vaccine.brandName) },
                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) } } else null,
                            onClick = { onVaccineToggled(vaccine); vaccineExpanded = false }
                        )
                    }
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
                            onClick = {
                                if (state.reminderId != null) vaccineToCancel = vaccine
                                else onVaccineToggled(vaccine)
                            },
                            label = { Text(vaccine.brandName) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (state.reminderId != null) vaccineToCancel = vaccine
                                        else onVaccineToggled(vaccine)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = if (state.reminderId != null) "Cancel ${vaccine.brandName}" else "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }

            DateDropdownPicker(
                label = "Due Date*",
                currentDate = state.dueDate,
                onDateSelected = onDueDateSelected
            )
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Next Vaccination") },
            text = { Text("Cancel this entire next vaccination visit and all its vaccines?") },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false; onCancel() }) {
                    Text("Cancel Vaccination", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep") }
            }
        )
    }

    vaccineToCancel?.let { vaccine ->
        AlertDialog(
            onDismissRequest = { vaccineToCancel = null },
            title = { Text("Cancel Vaccine") },
            text = { Text("Cancel ${vaccine.brandName} from this next vaccination? Other vaccines in this visit will remain active.") },
            confirmButton = {
                TextButton(onClick = { vaccineToCancel = null; onVaccineCancel(vaccine) }) {
                    Text("Cancel Vaccine", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { vaccineToCancel = null }) { Text("Keep") }
            }
        )
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
