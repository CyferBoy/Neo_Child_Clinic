package com.neochildclinic.features.personalreminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.DateDropdownPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPersonalReminderScreen(
    reminderId: String?,
    prefillPatientId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddEditPersonalReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(reminderId, prefillPatientId) {
        if (reminderId != null) {
            viewModel.loadForEdit(reminderId)
        } else if (prefillPatientId != null) {
            viewModel.preselectPatient(prefillPatientId)
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (uiState.isEditing) "Edit Personal Reminder" else "New Personal Reminder") },
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
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                SectionLabel("Patient")
                PatientPicker(
                    query = uiState.patientQuery,
                    onQueryChange = viewModel::onPatientQueryChange,
                    results = uiState.patientResults,
                    selectedPatient = uiState.selectedPatient,
                    onSelect = viewModel::selectPatient,
                    onClear = viewModel::clearSelectedPatient,
                    isError = uiState.patientError
                )

                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("Vaccine")
                VaccineDropdown(
                    vaccines = uiState.vaccines,
                    selectedVaccineId = uiState.selectedVaccineId,
                    onSelect = viewModel::selectVaccine,
                    isError = uiState.vaccineError
                )

                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("Requirement / Note (optional)")
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = viewModel::onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Patient requested vaccine; arrange from supplier.") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("Reminder / Follow-up Date")
                DateDropdownPicker(
                    label = "",
                    currentDate = uiState.reminderDate.ifBlank { AddEditPersonalReminderViewModel.todayFormatted() },
                    onDateSelected = viewModel::onReminderDateChange
                )
                if (uiState.reminderDateError) {
                    FieldError("Reminder date is required.")
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Advance Received")
                    Switch(
                        checked = uiState.advanceReceived,
                        onCheckedChange = viewModel::onAdvanceReceivedChange
                    )
                }

                if (uiState.advanceReceived) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.advanceAmount,
                        onValueChange = viewModel::onAdvanceAmountChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Advance Amount") },
                        leadingIcon = { Text("\u20b9", modifier = Modifier.padding(start = 12.dp)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = uiState.advanceAmountError,
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (uiState.advanceAmountError) {
                        FieldError("Enter a valid, non-negative advance amount.")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    DateDropdownPicker(
                        label = "Advance Date",
                        currentDate = uiState.advanceDate.ifBlank { AddEditPersonalReminderViewModel.todayFormatted() },
                        onDateSelected = viewModel::onAdvanceDateChange
                    )
                    if (uiState.advanceDateError) {
                        FieldError("Advance date is required.")
                    }
                }

                uiState.error?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (uiState.isEditing) "Save Changes" else "Create Reminder")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@Composable
private fun PatientPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<com.neochildclinic.domain.model.Patient>,
    selectedPatient: com.neochildclinic.domain.model.Patient?,
    onSelect: (com.neochildclinic.domain.model.Patient) -> Unit,
    onClear: () -> Unit,
    isError: Boolean
) {
    if (selectedPatient != null) {
        OutlinedCard(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(selectedPatient.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (selectedPatient.phone.isNotBlank()) {
                        Text(
                            selectedPatient.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    } else {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search patient by name or phone...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                isError = isError,
                shape = RoundedCornerShape(12.dp)
            )
            if (isError) {
                FieldError("Please select a patient.")
            }
            if (results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column {
                        results.take(6).forEach { patient ->
                            ListItem(
                                headlineContent = { Text(patient.name) },
                                supportingContent = if (patient.phone.isNotBlank()) {
                                    { Text(patient.phone) }
                                } else null,
                                modifier = Modifier.clickableSelect { onSelect(patient) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableSelect(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccineDropdown(
    vaccines: List<com.neochildclinic.data.local.entity.VaccineEntity>,
    selectedVaccineId: String?,
    onSelect: (String) -> Unit,
    isError: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when {
        selectedVaccineId == OTHER_VACCINE_SENTINEL -> "Other (specify in note)"
        selectedVaccineId != null -> vaccines.firstOrNull { it.id == selectedVaccineId }?.brandName ?: ""
        else -> ""
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            placeholder = { Text("Select a vaccine") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = isError,
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            vaccines.forEach { vaccine ->
                DropdownMenuItem(
                    text = { Text("${vaccine.brandName} (${vaccine.type})") },
                    onClick = {
                        onSelect(vaccine.id)
                        expanded = false
                    }
                )
            }
            if (vaccines.isNotEmpty()) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text("Other (specify in note)") },
                onClick = {
                    onSelect(OTHER_VACCINE_SENTINEL)
                    expanded = false
                }
            )
        }
    }
    if (isError) {
        FieldError("Please select a vaccine, or choose \"Other\".")
    }
}
