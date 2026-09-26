package com.neochildclinic.features.doctorslots

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.DoctorWeeklySlot

@Composable
internal fun WeeklySlotsTab(
    uiState: WeeklyDoctorSlotsUiState,
    onAddSlot: (Int, Int, Int) -> Unit,
    onRemoveSlot: (String) -> Unit,
    onToggleEditMode: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Weekly Slots", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (uiState.canManageSelectedDoctor) {
                TextButton(onClick = { onToggleEditMode(!uiState.isWeeklySlotsEditMode) }) {
                    Text(if (uiState.isWeeklySlotsEditMode) "Done" else "Edit")
                }
            }
        }

        WeeklyDoctorSlotsViewModel.WEEKDAYS.forEach { (dayOfWeek, dayName) ->
            DaySlotSection(
                dayName = dayName,
                dayOfWeek = dayOfWeek,
                slots = uiState.weeklySlots.filter { it.dayOfWeek == dayOfWeek },
                editable = uiState.isWeeklySlotsEditMode && uiState.canManageSelectedDoctor,
                onAddSlot = onAddSlot,
                onRemoveSlot = onRemoveSlot
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DaySlotSection(
    dayName: String,
    dayOfWeek: Int,
    slots: List<DoctorWeeklySlot>,
    editable: Boolean,
    onAddSlot: (Int, Int, Int) -> Unit,
    onRemoveSlot: (String) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var timePickerTarget by remember { mutableStateOf(0) }
    var pendingStart by remember { mutableIntStateOf(-1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(dayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            if (slots.isEmpty()) {
                Text(
                    "No availability",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                slots.sortedBy { it.startMinute }.forEach { slot ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "\u2022 ${slot.timeRange.label()}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                        )
                        if (editable) {
                            IconButton(
                                onClick = { onRemoveSlot(slot.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove slot",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (editable) {
                TextButton(
                    onClick = { showTimePicker = true; timePickerTarget = 0; pendingStart = -1 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add slot")
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = 9,
            initialMinute = 0,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = {
                Text(if (pendingStart < 0) "Select Start Time" else "Select End Time")
            },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMinute = timePickerState.hour * 60 + timePickerState.minute
                    if (pendingStart < 0) {
                        pendingStart = selectedMinute
                        timePickerTarget = 1
                    } else {
                        if (selectedMinute > pendingStart) {
                            onAddSlot(dayOfWeek, pendingStart, selectedMinute)
                        }
                        showTimePicker = false
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}