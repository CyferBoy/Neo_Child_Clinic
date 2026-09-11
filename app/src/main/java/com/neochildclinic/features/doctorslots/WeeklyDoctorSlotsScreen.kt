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
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.DoctorDropdown
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.SlotExceptionType
import com.neochildclinic.domain.model.TimeRange
import java.text.SimpleDateFormat
import java.util.*

/**
 * Weekly Doctor Slots (req. 4-10): view/configure a doctor's normal recurring weekly
 * availability, plus date-specific exceptions. The "Edit Slot" button (top-right) is only
 * shown to admin/doctor (req. 5); the ViewModel also refuses writes from anyone else, so
 * this is enforced beyond just hiding the button (req. 19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDoctorSlotsScreen(
    onBack: () -> Unit,
    viewModel: WeeklyDoctorSlotsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddExceptionDialog by remember { mutableStateOf(false) }

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
                    title = { Text("Weekly Doctor Slots") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        if (uiState.canManageSelectedDoctor) {
                            TextButton(onClick = { viewModel.setEditMode(!uiState.isEditMode) }) {
                                Text(if (uiState.isEditMode) "Done" else "Edit Slot")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            if (uiState.allDoctors.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No doctor accounts found.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                return@Scaffold
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                if (uiState.allDoctors.size > 1) {
                    DoctorDropdown(
                        doctors = uiState.allDoctors,
                        selectedDoctor = uiState.selectedDoctor,
                        onDoctorSelected = { viewModel.selectDoctor(it) }
                    )
                } else {
                    Text(
                        uiState.selectedDoctor?.displayName ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                Text("Weekly Availability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                WeeklyDoctorSlotsViewModel.WEEKDAYS.forEach { (dayOfWeek, dayName) ->
                    DaySlotSection(
                        dayName = dayName,
                        dayOfWeek = dayOfWeek,
                        activeRanges = uiState.weeklySlots
                            .filter { it.dayOfWeek == dayOfWeek }
                            .map { it.timeRange }
                            .toSet(),
                        editable = uiState.isEditMode && uiState.canManageSelectedDoctor,
                        onToggle = { range, enabled -> viewModel.toggleSlot(dayOfWeek, range, enabled) }
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date Exceptions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (uiState.isEditMode && uiState.canManageSelectedDoctor) {
                        TextButton(onClick = { showAddExceptionDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add Exception")
                        }
                    }
                }

                if (uiState.exceptions.isEmpty()) {
                    Text(
                        "No date exceptions configured.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    uiState.exceptions.sortedByDescending { it.exceptionDate }.forEach { exception ->
                        ExceptionRow(
                            exception = exception,
                            weeklySlotLabel = uiState.weeklySlots.firstOrNull { it.id == exception.weeklySlotId }?.timeRange?.label(),
                            canDelete = uiState.isEditMode && uiState.canManageSelectedDoctor,
                            onDelete = { viewModel.deleteException(exception.id) }
                        )
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showAddExceptionDialog) {
        AddExceptionDialog(
            weeklySlots = uiState.weeklySlots,
            onDismiss = { showAddExceptionDialog = false },
            onAddFullDay = { date, reason ->
                viewModel.addFullDayException(date, reason)
                showAddExceptionDialog = false
            },
            onAddSlot = { date, slotId, reason ->
                viewModel.addSlotException(date, slotId, reason)
                showAddExceptionDialog = false
            }
        )
    }
}

@Composable
private fun DaySlotSection(
    dayName: String,
    dayOfWeek: Int,
    activeRanges: Set<TimeRange>,
    editable: Boolean,
    onToggle: (TimeRange, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(dayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            if (!editable && activeRanges.isEmpty()) {
                Text(
                    "No availability",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            WeeklyDoctorSlotsViewModel.PREDEFINED_RANGES.forEach { range ->
                val checked = activeRanges.contains(range)
                if (editable) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(range, it) })
                        Text(range.label(), style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (checked) {
                    Text(
                        "\u2022 ${range.label()}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                    )
                }
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
                    if (exception.exceptionType == SlotExceptionType.FULL_DAY) {
                        "Entire day unavailable"
                    } else {
                        "${weeklySlotLabel ?: "Slot"} unavailable"
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
    weeklySlots: List<com.neochildclinic.domain.model.DoctorWeeklySlot>,
    onDismiss: () -> Unit,
    onAddFullDay: (date: String, reason: String?) -> Unit,
    onAddSlot: (date: String, slotId: String, reason: String?) -> Unit
) {
    val today = remember { SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date()) }
    var date by remember { mutableStateOf(today) }
    var fullDay by remember { mutableStateOf(true) }
    var selectedSlotId by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var slotMenuExpanded by remember { mutableStateOf(false) }

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
                    RadioButton(selected = !fullDay, onClick = { fullDay = false }, enabled = weeklySlots.isNotEmpty())
                    Text("Specific slot unavailable", modifier = Modifier.padding(start = 4.dp))
                }

                if (!fullDay) {
                    ExposedDropdownMenuBox(
                        expanded = slotMenuExpanded,
                        onExpandedChange = { slotMenuExpanded = !slotMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = weeklySlots.firstOrNull { it.id == selectedSlotId }?.timeRange?.label() ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Slot") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = slotMenuExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = slotMenuExpanded, onDismissRequest = { slotMenuExpanded = false }) {
                            weeklySlots.forEach { slot ->
                                DropdownMenuItem(
                                    text = { Text(slot.timeRange.label()) },
                                    onClick = { selectedSlotId = slot.id; slotMenuExpanded = false }
                                )
                            }
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
                    } else {
                        selectedSlotId?.let { onAddSlot(date, it, reason) }
                    }
                },
                enabled = fullDay || selectedSlotId != null
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
