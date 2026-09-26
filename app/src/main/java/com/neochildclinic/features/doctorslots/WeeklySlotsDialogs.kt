package com.neochildclinic.features.doctorslots

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.domain.model.TimeRange
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddExceptionDialog(
    onDismiss: () -> Unit,
    onAddFullDay: (date: String, reason: String?) -> Unit,
    onAddCustomTime: (date: String, startMinute: Int, endMinute: Int, reason: String?) -> Unit
) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern(Constants.DATE_FORMAT, Locale.ENGLISH)) }
    var date by remember { mutableStateOf(today) }
    var fullDay by remember { mutableStateOf(true) }
    var reason by remember { mutableStateOf("") }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var startMinute by remember { mutableIntStateOf(9 * 60) }
    var endMinute by remember { mutableIntStateOf(10 * 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exception") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateDropdownPicker(label = "Date", currentDate = date, onDateSelected = { date = it })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = fullDay, onClick = { fullDay = true })
                    Text("Entire day unavailable", modifier = Modifier.padding(start = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !fullDay, onClick = { fullDay = false })
                    Text("Custom time unavailable", modifier = Modifier.padding(start = 4.dp))
                }

                if (!fullDay) {
                    OutlinedTextField(
                        value = TimeRange(startMinute, endMinute).label(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Time Range") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showStartTimePicker = true }) {
                            Text("Start: ${TimeRange.formatMinuteOfDay(startMinute)}")
                        }
                        OutlinedButton(onClick = { showEndTimePicker = true }) {
                            Text("End: ${TimeRange.formatMinuteOfDay(endMinute)}")
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fullDay) {
                        onAddFullDay(date, reason)
                    } else if (endMinute > startMinute) {
                        onAddCustomTime(date, startMinute, endMinute, reason)
                    }
                },
                enabled = fullDay || endMinute > startMinute
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = startMinute / 60,
            initialMinute = startMinute % 60,
            onDismiss = { showStartTimePicker = false },
            onTimeSelected = { h, m ->
                startMinute = h * 60 + m
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = endMinute / 60,
            initialMinute = endMinute % 60,
            onDismiss = { showEndTimePicker = false },
            onTimeSelected = { h, m ->
                endMinute = h * 60 + m
                showEndTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = { onTimeSelected(timePickerState.hour, timePickerState.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}