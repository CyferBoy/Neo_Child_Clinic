package com.neochildclinic.features.dashboard

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
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
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
    val isoFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH) }
    val initial = remember(currentDate) {
        try {
            java.time.LocalDate.parse(currentDate, isoFmt)
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
                onConfirm(date.withDayOfMonth(1).format(isoFmt))
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
        title = { Text("Select Type", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSelect(TodayPatientTab.CONSULTATION) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalCustomColors.current.softBlue, contentColor = LocalCustomColors.current.textBlue)
                ) {
                    Text("Consultation")
                }
                Button(
                    onClick = { onSelect(TodayPatientTab.VACCINATION) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalCustomColors.current.softGreen, contentColor = LocalCustomColors.current.textGreen)
                ) {
                    Text("Vaccination")
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
                is ConsultationTodoEntity -> initialItem.name
                is VaccinationTodoEntity -> initialItem.name
                else -> ""
            }
        )
    }
    var mobile by rememberSaveable {
        mutableStateOf(
            when (initialItem) {
                is ConsultationTodoEntity -> initialItem.mobile
                is VaccinationTodoEntity -> initialItem.mobile
                else -> ""
            }
        )
    }
    var address by rememberSaveable {
        mutableStateOf(
            when (initialItem) {
                is ConsultationTodoEntity -> initialItem.address
                is VaccinationTodoEntity -> initialItem.address
                else -> ""
            }
        )
    }
    var vaccineNames by rememberSaveable {
        mutableStateOf(
            when (initialItem) {
                is VaccinationTodoEntity -> initialItem.vaccineNames
                else -> ""
            }
        )
    }
    var selectedPatientId by remember {
        mutableStateOf(
            when (initialItem) {
                is ConsultationTodoEntity -> initialItem.patientId
                is VaccinationTodoEntity -> initialItem.patientId
                else -> null
            }
        )
    }
    val initialDoctorId = remember(initialItem) {
        when (initialItem) {
            is ConsultationTodoEntity -> initialItem.doctorId
            is VaccinationTodoEntity -> initialItem.doctorId
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
            is ConsultationTodoEntity -> initialItem.availabilitySlotId
            is VaccinationTodoEntity -> initialItem.availabilitySlotId
            else -> null
        }
        if (selectedSlot == null && !savedSlotId.isNullOrBlank()) {
            (slotsState as? SlotsUiState.Loaded)?.slots
                ?.firstOrNull { it.weeklySlotId == savedSlotId }
                ?.let { selectedSlot = it }
        }
    }

    // Loads (or reloads) availability whenever the selected doctor changes - covers both
    // the edit-mode preselect (once allDoctors has loaded and the match is found) and a
    // fresh manual pick from the dropdown below.
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
        title = {
            val prefix = if (initialItem == null) "Add" else "Edit"
            Text(if (type == TodayPatientTab.CONSULTATION) "$prefix Consultation" else "$prefix Vaccination")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            selectedPatientId = null // Reset if typing manually
                        },
                        label = { Text("Patient Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (suggestions.isNotEmpty() && selectedPatientId == null) {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column {
                                suggestions.forEach { patient ->
                                    ListItem(
                                        headlineContent = { Text(patient.name) },
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

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                if (type == TodayPatientTab.VACCINATION) {
                    OutlinedTextField(
                        value = vaccineNames,
                        onValueChange = { vaccineNames = it },
                        label = { Text("Vaccine Name(s)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. MMR, DPT") }
                    )
                }

                // Doctor assignment (req. 15/16) - optional at this quick-add stage.
                // Leaving it unset keeps existing behavior (notification broadcasts to
                // every doctor); picking one targets that doctor's device specifically.
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
            Button(
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