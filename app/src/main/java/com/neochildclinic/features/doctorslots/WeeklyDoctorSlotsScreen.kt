package com.neochildclinic.features.doctorslots

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.DoctorDropdown
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.ui.DeleteConfirmationDialog
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.DoctorWeeklySlot
import com.neochildclinic.domain.model.SlotExceptionType
import com.neochildclinic.domain.model.TimeRange
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDoctorSlotsScreen(
    onBack: () -> Unit,
    viewModel: WeeklyDoctorSlotsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddExceptionDialog by remember { mutableStateOf(false) }
    var exceptionToDelete by remember { mutableStateOf<DoctorSlotException?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Doctor Timings") },
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
            val isRefreshing by viewModel.isRefreshing.collectAsState()
            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 6,
                        cardShaped = true,
                        contentPadding = PaddingValues(16.dp)
                    )
                } else if (uiState.allDoctors.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No doctor accounts found.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        DoctorDropdown(
                            doctors = uiState.allDoctors,
                            selectedDoctor = uiState.selectedDoctor,
                            onDoctorSelected = { viewModel.selectDoctor(it) }
                        )

                        SegmentedControl(
                            selectedTab = uiState.selectedTab,
                            onTabSelected = { viewModel.selectTab(it) }
                        )

                        when (uiState.selectedTab) {
                            0 -> WeeklySlotsTab(
                                uiState = uiState,
                                onAddSlot = { dayOfWeek, start, end -> viewModel.addWeeklySlot(dayOfWeek, start, end) },
                                onRemoveSlot = { viewModel.removeWeeklySlot(it) },
                                onToggleEditMode = { viewModel.setWeeklySlotsEditMode(it) }
                            )
                            1 -> UnavailabilityTab(
                                uiState = uiState,
                                onAddException = { showAddExceptionDialog = true },
                                onDeleteException = { viewModel.deleteException(it) }
                            )
                        }

                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showAddExceptionDialog) {
        AddExceptionDialog(
            onDismiss = { showAddExceptionDialog = false },
            onAddFullDay = { date, reason ->
                viewModel.addFullDayException(date, reason)
                showAddExceptionDialog = false
            },
            onAddCustomTime = { date, start, end, reason ->
                viewModel.addCustomTimeException(date, start, end, reason)
                showAddExceptionDialog = false
            }
        )
    }

    DeleteConfirmationDialog(
        show = exceptionToDelete != null,
        onDismiss = { exceptionToDelete = null },
        onConfirm = {
            exceptionToDelete?.let { viewModel.deleteException(it.id) }
            exceptionToDelete = null
        },
        title = "Delete Exception",
        message = "Are you sure you want to delete this date exception?"
    )
}

@Composable
private fun SegmentedControl(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            onClick = { onTabSelected(0) },
            selected = selectedTab == 0,
            label = { Text("Weekly Slots") }
        )
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            onClick = { onTabSelected(1) },
            selected = selectedTab == 1,
            label = { Text("Unavailability") }
        )
    }
}

@Composable
private fun WeeklySlotsTab(
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
private fun DaySlotSection(
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

@Composable
private fun UnavailabilityTab(
    uiState: WeeklyDoctorSlotsUiState,
    onAddException: () -> Unit,
    onDeleteException: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Unavailability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (uiState.canManageSelectedDoctor) {
                TextButton(onClick = onAddException) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Exception")
                }
            }
        }

        if (uiState.exceptions.isEmpty()) {
            Text(
                "No exceptions configured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            uiState.exceptions.sortedByDescending { it.exceptionDate }.forEach { exception ->
                ExceptionRow(
                    exception = exception,
                    weeklySlotLabel = uiState.weeklySlots.firstOrNull { it.id == exception.weeklySlotId }?.timeRange?.label(),
                    canDelete = uiState.canManageSelectedDoctor,
                    onDelete = { onDeleteException(exception.id) }
                )
            }
        }
    }
}

@Composable
private fun ExceptionRow(
    exception: DoctorSlotException,
    weeklySlotLabel: String?,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(exception.exceptionDate, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        exception.exceptionType == SlotExceptionType.FULL_DAY -> "Entire day unavailable"
                        exception.timeRangeLabel != null -> "Unavailable ${exception.timeRangeLabel}"
                        weeklySlotLabel != null -> "$weeklySlotLabel unavailable"
                        else -> "Slot unavailable"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (!exception.reason.isNullOrBlank()) {
                    Text("Reason: ${exception.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete exception", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExceptionDialog(
    onDismiss: () -> Unit,
    onAddFullDay: (date: String, reason: String?) -> Unit,
    onAddCustomTime: (date: String, startMinute: Int, endMinute: Int, reason: String?) -> Unit
) {
    val today = remember { SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date()) }
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
private fun TimePickerDialog(
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
