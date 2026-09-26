package com.neochildclinic.features.vaccination

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.*
import com.neochildclinic.domain.model.Patient

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
                BackTopAppBar(
                    title = { Text(if (isEdit) "Edit Vaccination" else "Add Vaccination") },
                    onBack = onBack,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
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
                            onClick = {
                                viewModel.saveVaccination(
                                    editGivenDate = editGivenDate,
                                    editDoctor = editDoctor,
                                    editVaccineBatch = editVaccineBatch,
                                    editQuantity = editQuantity,
                                    editNextVaccination = editNextVaccination
                                )
                            },
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
                        Spacer(Modifier.height(8.dp))
                    }
                    // The slot dropdown must be visible whenever the schedule is being
                    // edited - including a date-only edit, where the Doctor list stays
                    // hidden but the change has already cleared the recorded slot and the
                    // save requires a new one.
                    if (!isEdit || editDoctor || editGivenDate) {
                        AvailableSlotDropdown(
                            state = uiState.slotsState,
                            selectedSlot = uiState.selectedSlot,
                            onSlotSelected = { viewModel.selectSlot(it) },
                            isError = uiState.slotError
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
                        ReadOnlyValue(if (uiState.nextVaccinationGroups.isEmpty()) "No next vaccination" else uiState.nextVaccinationGroups.joinToString("\n") { group ->
                            val itemsStr = group.items.joinToString(", ") { it.vaccine?.brandName ?: it.type }
                            "$itemsStr • Due: ${group.dueDate}"
                        })
                    }
                }
                if (!isEdit || editNextVaccination) {
                    items(uiState.nextVaccinationGroups, key = { it.id }) { group ->
                        NextVaccinationGroupCard(
                            group = group,
                            inventory = uiState.inventory,
                            availableTypes = uiState.availableDueTypes,
                            onDueDateSelected = { viewModel.updateNextVaccinationGroupDate(group.id, it) },
                            onAddItem = { viewModel.addNextVaccinationItem(group.id) },
                            onRemoveGroup = { viewModel.removeNextVaccinationGroup(group.id) },
                            onCancelGroup = { viewModel.cancelNextVaccinationGroup(group.id) },
                            onTypeSelected = { itemId, type -> viewModel.updateNextVaccinationItem(group.id, itemId, type = type) },
                            onVaccineSelected = { itemId, vaccine -> viewModel.updateNextVaccinationItem(group.id, itemId, vaccine = vaccine) },
                            onRemoveItem = { itemId -> viewModel.removeNextVaccinationItem(group.id, itemId) },
                            onCancelItem = { itemId -> viewModel.cancelNextVaccinationItem(group.id, itemId) }
                        )
                    }
                    item {
                        OutlinedButton(onClick = { viewModel.addNextVaccinationGroup() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.nextVaccinationGroups.isEmpty()) "Add Next Vaccination" else "Add Another Date")
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
