package com.clinic.neochild.features.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clinic.neochild.domain.model.ReminderStatus
import com.clinic.neochild.domain.model.VaccinationSource
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDueBottomSheet(
    status: ReminderStatus,
    onDismiss: () -> Unit,
    onMarkAsDone: () -> Unit,
    onDismissReminder: () -> Unit,
    onReschedule: () -> Unit,
    onVaccinatedElsewhere: () -> Unit,
    onRestore: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            val title = when (status) {
                ReminderStatus.ACTIVE, ReminderStatus.RESCHEDULED -> "Manage Due Vaccination"
                ReminderStatus.COMPLETED -> "Completed Vaccination"
                ReminderStatus.DISMISSED -> "Dismissed Reminder"
                ReminderStatus.EXTERNAL -> "External Vaccination"
                else -> "Manage Reminder"
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            
            if (status == ReminderStatus.ACTIVE || status == ReminderStatus.RESCHEDULED) {
                ListItem(
                    headlineContent = { Text("Mark as Done") },
                    supportingContent = { Text("Given in this clinic today") },
                    leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)) },
                    modifier = Modifier.clickable { onMarkAsDone() }
                )
                ListItem(
                    headlineContent = { Text("Dismiss Reminder") },
                    supportingContent = { Text("Stop reminders for this vaccine") },
                    leadingContent = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                    modifier = Modifier.clickable { onDismissReminder() }
                )
                ListItem(
                    headlineContent = { Text("Reschedule") },
                    supportingContent = { Text("Change the due date") },
                    leadingContent = { Icon(Icons.Default.Event, contentDescription = null) },
                    modifier = Modifier.clickable { onReschedule() }
                )
                ListItem(
                    headlineContent = { Text("Vaccinated Elsewhere") },
                    supportingContent = { Text("Recorded at another facility") },
                    leadingContent = { Icon(Icons.Default.Public, contentDescription = null) },
                    modifier = Modifier.clickable { onVaccinatedElsewhere() }
                )
            } else {
                ListItem(
                    headlineContent = { Text("Restore to Active") },
                    supportingContent = { Text("Move back to due/overdue schedule") },
                    leadingContent = { Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onRestore() }
                )
            }

            ListItem(
                headlineContent = { Text("Cancel") },
                leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccinatedElsewhereBottomSheet(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var hospitalName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date())) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(it))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Vaccinated Elsewhere",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = hospitalName,
                onValueChange = { hospitalName = it },
                label = { Text("Hospital / Clinic Name") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Civil Hospital, private clinic etc.") }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vaccination Date: $selectedDate")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Optional Notes") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { onSave(hospitalName, selectedDate, notes) },
                enabled = hospitalName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
