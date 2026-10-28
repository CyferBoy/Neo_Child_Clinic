package com.neochildclinic.feature.doctor.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.Spacing
import com.neochildclinic.domain.model.DoctorWeeklySlot

@Composable
internal fun WeeklySlotsTab(
    uiState: WeeklyDoctorSlotsUiState,
    onAddSlot: (Int, Int, Int) -> Unit,
    onRemoveSlot: (String) -> Unit,
    onToggleEditMode: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Weekly slots",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (uiState.canManageSelectedDoctor) {
                TextButton(onClick = { onToggleEditMode(!uiState.isWeeklySlotsEditMode) }) {
                    Text(
                        text = if (uiState.isWeeklySlotsEditMode) "Done" else "Edit slots",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Unified 20dp Card for all 7 weekdays matching doctor_timings_light.svg
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                WeeklyDoctorSlotsViewModel.WEEKDAYS.forEachIndexed { index, (dayOfWeek, dayName) ->
                    DaySlotRow(
                        dayName = dayName,
                        dayOfWeek = dayOfWeek,
                        slots = uiState.weeklySlots.filter { it.dayOfWeek == dayOfWeek },
                        editable = uiState.isWeeklySlotsEditMode && uiState.canManageSelectedDoctor,
                        onAddSlot = onAddSlot,
                        onRemoveSlot = onRemoveSlot
                    )
                    if (index < WeeklyDoctorSlotsViewModel.WEEKDAYS.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Spacing.card),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DaySlotRow(
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

    val hasSlots = slots.isNotEmpty()
    val slotText = remember(slots) {
        if (slots.isEmpty()) "Unavailable"
        else slots.sortedBy { it.startMinute }.joinToString(", ") { it.timeRange.label() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.card, vertical = Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasSlots) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(90.dp)
            )

            Text(
                text = slotText,
                style = MaterialTheme.typography.bodySmall,
                color = if (hasSlots) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )

            if (editable) {
                IconButton(
                    onClick = {
                        showTimePicker = true
                        timePickerTarget = 0
                        pendingStart = -1
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add slot",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (hasSlots) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(width = 36.dp, height = 22.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp),
                        contentAlignment = if (hasSlots) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(16.dp)
                        ) {}
                    }
                }
            }
        }

        // When in edit mode, display each individual slot chip with remove action
        if (editable && hasSlots) {
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                slots.sortedBy { it.startMinute }.forEach { slot ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = slot.timeRange.label(),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onRemoveSlot(slot.id) },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove slot",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
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
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = if (pendingStart < 0) "Select Start Time" else "Select End Time",
                    fontWeight = FontWeight.Bold
                )
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
                    Text("OK", fontWeight = FontWeight.Bold)
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
