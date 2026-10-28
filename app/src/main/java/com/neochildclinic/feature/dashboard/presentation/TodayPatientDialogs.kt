package com.neochildclinic.feature.dashboard.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.LocalCustomColors
import com.neochildclinic.core.ui.AvailableSlotDropdown
import com.neochildclinic.core.ui.DoctorDropdown
import com.neochildclinic.core.ui.SlotsUiState
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.domain.model.ConsultationTodo
import com.neochildclinic.domain.model.VaccinationTodo
import com.neochildclinic.domain.model.AvailableSlot
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Profile
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class TodayPatientTab { CONSULTATION, VACCINATION }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MonthYearPickerDialog(
    currentDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    
    val initial = remember(currentDate) {
        try {
            java.time.LocalDate.parse(currentDate)
        } catch (_: java.time.format.DateTimeParseException) {
            java.time.LocalDate.now()
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val date = state.selectedDateMillis?.let {
                    java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                } ?: initial
                onConfirm(date.withDayOfMonth(1).toString())
            }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
internal fun AddTypeSelectionDialog(
    onDismiss: () -> Unit,
    onSelect: (TodayPatientTab) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text("Select Type", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Button(
                    onClick = { onSelect(TodayPatientTab.CONSULTATION) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Consultation", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onSelect(TodayPatientTab.VACCINATION) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("Vaccination", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun EnhancedAddTodoDialog(
    type: TodayPatientTab,
    patients: List<Patient>,
    initialItem: Any? = null,
    allDoctors: List<Profile>,
    slotsState: SlotsUiState,
    onDoctorSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, mobile: String, address: String, vaccineNames: String, patientId: String?, doctorId: String?, doctorName: String?, slotId: String?) -> Unit
) {
    var name by rememberSaveable {
        mutableStateOf(
            when (initialItem) {
                is ConsultationTodo -> initialItem.name
                is VaccinationTodo -> initialItem.name
                else -> ""
            }
        )
    }
    var mobile by rememberSaveable {
        mutableStateOf(
            when (initialItem) {
                is ConsultationTodo -> initialItem.mobile
                is VaccinationTodo -> initialItem.mobile
                else -> ""
            }
        )
    }
    var address by rememberSaveable {
        mutableStateOf(
            when (initialItem) {
                is ConsultationTodo -> initialItem.address
                is VaccinationTodo -> initialItem.address
                else -> ""
            }
        )
    }
    var vaccineNames by rememberSaveable {
        mutableStateOf(
            when (initialItem) {
                is VaccinationTodo -> initialItem.vaccineNames
                else -> ""
            }
        )
    }
    var selectedPatientId by remember {
        mutableStateOf(
            when (initialItem) {
                is ConsultationTodo -> initialItem.patientId
                is VaccinationTodo -> initialItem.patientId
                else -> null
            }
        )
    }
    val initialDoctorId = remember(initialItem) {
        when (initialItem) {
            is ConsultationTodo -> initialItem.doctorId
            is VaccinationTodo -> initialItem.doctorId
            else -> null
        }
    }
    var selectedDoctor by remember(allDoctors) {
        mutableStateOf(allDoctors.firstOrNull { it.id == initialDoctorId })
    }
    var selectedSlot by remember { mutableStateOf<AvailableSlot?>(null) }

    // Preselect the previously saved slot once availability has loaded for this doctor.
    LaunchedEffect(slotsState, initialItem) {
        val savedSlotId = when (initialItem) {
            is ConsultationTodo -> initialItem.availabilitySlotId
            is VaccinationTodo -> initialItem.availabilitySlotId
            else -> null
        }
        if (selectedSlot == null && !savedSlotId.isNullOrBlank()) {
            (slotsState as? SlotsUiState.Loaded)?.slots
                ?.firstOrNull { it.weeklySlotId == savedSlotId }
                ?.let { selectedSlot = it }
        }
    }

    LaunchedEffect(selectedDoctor?.id) {
        selectedDoctor?.let { onDoctorSelected(it.id) }
    }

    val suggestions = remember(name, patients) {
        if (name.length >= 2) {
            patients.filter { it.name.contains(name, ignoreCase = true) }.take(5)
        } else emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = {
            val prefix = if (initialItem == null) "Add" else "Edit"
            Text(
                if (type == TodayPatientTab.CONSULTATION) "$prefix Consultation" else "$prefix Vaccination",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Column {
                    StandardTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            selectedPatientId = null
                        },
                        label = "Patient Name"
                    )
                    if (suggestions.isNotEmpty() && selectedPatientId == null) {
                        Card(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)
                        ) {
                            Column {
                                suggestions.forEach { patient ->
                                    ListItem(
                                        headlineContent = { Text(patient.name, fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text(patient.phone) },
                                        modifier = Modifier.clickable {
                                            name = patient.name
                                            mobile = patient.phone
                                            address = patient.address.orEmpty()
                                            selectedPatientId = patient.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                StandardTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = "Mobile Number"
                )

                StandardTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Address",
                    minLines = 2
                )

                if (type == TodayPatientTab.VACCINATION) {
                    StandardTextField(
                        value = vaccineNames,
                        onValueChange = { vaccineNames = it },
                        label = "Vaccine Name(s)",
                        placeholder = "e.g. MMR, DPT"
                    )
                }

                DoctorDropdown(
                    doctors = allDoctors,
                    selectedDoctor = selectedDoctor,
                    onDoctorSelected = { doctor ->
                        selectedDoctor = doctor
                        selectedSlot = null
                    }
                )

                if (selectedDoctor != null) {
                    AvailableSlotDropdown(
                        state = slotsState,
                        selectedSlot = selectedSlot,
                        onSlotSelected = { selectedSlot = it },
                        label = "Available Slot (optional)"
                    )
                }
            }
        },
        confirmButton = {
            StandardButton(
                enabled = name.isNotBlank() && mobile.isNotBlank() && (type == TodayPatientTab.CONSULTATION || vaccineNames.isNotBlank()),
                onClick = {
                    onConfirm(
                        name, mobile, address, vaccineNames, selectedPatientId,
                        selectedDoctor?.id, selectedDoctor?.displayName, selectedSlot?.weeklySlotId
                    )
                }
            ) {
                Text(if (initialItem == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}