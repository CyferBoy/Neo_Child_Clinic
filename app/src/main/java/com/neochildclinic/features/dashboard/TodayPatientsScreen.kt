package com.neochildclinic.features.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.content.Intent
import android.net.Uri

private enum class TodayPatientTab { CONSULTATION, VACCINATION }

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalFoundationApi::class)
@Composable
fun TodayPatientsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
    // Set from a "New Consultation/Vaccination Patient" notification tap (req. 9), via
    // Routes.TODAY_PATIENTS's optional ?tab=&highlightId= args. Both are null for every
    // other entry point (the dashboard's own "Today's Patients" tile), so the default
    // (CONSULTATION, no highlight) is unchanged for that path.
    initialTab: String? = null,
    highlightId: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var selectedTab by rememberSaveable {
        mutableStateOf(
            if (initialTab == "vaccination") TodayPatientTab.VACCINATION else TodayPatientTab.CONSULTATION
        )
    }
    var showSelectionDialog by remember { mutableStateOf(false) }
    var showAddDialogForType by remember { mutableStateOf<TodayPatientTab?>(null) }
    var editingTodo by remember { mutableStateOf<Any?>(null) }
    var showMonthYearPicker by remember { mutableStateOf(false) }
    // Cleared once consumed so it only scrolls/highlights on the notification-driven
    // arrival, not again on every later recomposition (e.g. after toggling an item's status).
    var pendingHighlightId by remember(highlightId) { mutableStateOf(highlightId) }

    val pendingList = if (selectedTab == TodayPatientTab.CONSULTATION) uiState.todayConsultations else uiState.todayVaccinations
    val visitedList = if (selectedTab == TodayPatientTab.CONSULTATION) uiState.visitedConsultations else uiState.visitedVaccinations
    val customColors = LocalCustomColors.current
    val listState = rememberLazyListState()

    // If possible, use the included todo ID to open the exact entry (req. 9). The item may
    // be on this device's Room a moment later than the push itself (still mid-download via
    // the realtime-triggered refresh()), so this simply does nothing until it appears -
    // there is no separate fetch-by-id path to add here.
    LaunchedEffect(pendingHighlightId, pendingList, visitedList) {
        val targetId = pendingHighlightId ?: return@LaunchedEffect
        val pendingIndex = pendingList.indexOfFirst { item ->
            when (item) {
                is ConsultationTodoEntity -> item.id == targetId
                is VaccinationTodoEntity -> item.id == targetId
                else -> false
            }
        }
        val found = if (pendingIndex >= 0) {
            listState.animateScrollToItem(pendingIndex)
            true
        } else {
            val visitedIndex = visitedList.indexOfFirst { item ->
                when (item) {
                    is ConsultationTodoEntity -> item.id == targetId
                    is VaccinationTodoEntity -> item.id == targetId
                    else -> false
                }
            }
            // +1 for the "Visited" section header item emitted just before this list below.
            if (visitedIndex >= 0) {
                listState.animateScrollToItem(pendingList.size + 1 + visitedIndex)
                true
            } else {
                false
            }
        }
        if (found) {
            kotlinx.coroutines.delay(2500)
            pendingHighlightId = null
        }
    }

    val displayMonthYear = remember(selectedDate) {
        val date = try {
            java.time.LocalDate.parse(selectedDate, DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH))
        } catch (_: java.time.format.DateTimeParseException) {
            java.time.LocalDate.now()
        }
        date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(customColors.bgOffWhite)) {
                BackTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showMonthYearPicker = true }
                        ) {
                            Text(
                                displayMonthYear,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    onBack = onBack,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = customColors.bgOffWhite,
                        titleContentColor = customColors.iconColor,
                        navigationIconContentColor = customColors.iconColor
                    )
                )
                HorizontalDateSelector(
                    selectedDate = selectedDate,
                    datesWithData = uiState.datesWithData,
                    onDateSelected = { viewModel.setSelectedDate(it) }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    editingTodo = null
                    showSelectionDialog = true 
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Patient Entry")
            }
        },
        containerColor = customColors.bgOffWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == TodayPatientTab.CONSULTATION,
                    onClick = { selectedTab = TodayPatientTab.CONSULTATION },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    modifier = Modifier.weight(1f)
                ) { Text("Consultation") }
                SegmentedButton(
                    selected = selectedTab == TodayPatientTab.VACCINATION,
                    onClick = { selectedTab = TodayPatientTab.VACCINATION },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    modifier = Modifier.weight(1f)
                ) { Text("Vaccination") }
            }

            // Dynamic slot filter: one segment per available slot for the selected date
            // (0/1 slots -> hidden, 2+ -> shown). Labels and count come from existing
            // availability/booking data; the row scrolls instead of shrinking/clipping
            // when there are many segments.
            if (uiState.slotSegments.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(
                    Modifier.horizontalScroll(rememberScrollState())
                ) {
                    uiState.slotSegments.forEachIndexed { index, segment ->
                        SegmentedButton(
                            selected = uiState.selectedSlotKey == segment.key,
                            onClick = { viewModel.setSelectedSlot(segment.key) },
                            shape = SegmentedButtonDefaults.itemShape(index, uiState.slotSegments.size)
                        ) { Text(segment.label) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                if (pendingList.isEmpty() && visitedList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No patients added for this date",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = pendingList,
                        key = { _, item ->
                            when (item) {
                                is ConsultationTodoEntity -> "c_${item.id}"
                                is VaccinationTodoEntity -> "v_${item.id}"
                                else -> item.hashCode()
                            }
                        }
                    ) { index, item ->
                        val itemId = when (item) {
                            is ConsultationTodoEntity -> item.id
                            is VaccinationTodoEntity -> item.id
                            else -> null
                        }
                        TodayPatientItem(
                            index = index + 1,
                            item = item,
                            isHighlighted = itemId != null && itemId == pendingHighlightId,
                            onStatusToggle = { viewModel.toggleTodoStatus(item) },
                            onDelete = {
                                when (item) {
                                    is ConsultationTodoEntity -> viewModel.deleteConsultation(item.id)
                                    is VaccinationTodoEntity -> viewModel.deleteVaccination(item.id)
                                }
                            },
                            onEdit = { editingTodo = item }
                        )
                    }

                    if (visitedList.isNotEmpty()) {
                        item {
                            Text(
                                "Visited",
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }

                        itemsIndexed(
                            items = visitedList,
                            key = { _, item ->
                                when (item) {
                                    is ConsultationTodoEntity -> "cv_${item.id}"
                                    is VaccinationTodoEntity -> "vv_${item.id}"
                                    else -> item.hashCode()
                                }
                            }
                        ) { index, item ->
                            val itemId = when (item) {
                                is ConsultationTodoEntity -> item.id
                                is VaccinationTodoEntity -> item.id
                                else -> null
                            }
                            TodayPatientItem(
                                index = index + 1,
                                item = item,
                                isHighlighted = itemId != null && itemId == pendingHighlightId,
                                onStatusToggle = { viewModel.toggleTodoStatus(item) },
                                onDelete = {
                                    when (item) {
                                        is ConsultationTodoEntity -> viewModel.deleteConsultation(item.id)
                                        is VaccinationTodoEntity -> viewModel.deleteVaccination(item.id)
                                    }
                                },
                                onEdit = { editingTodo = item }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    if (showMonthYearPicker) {
        MonthYearPickerDialog(
            currentDate = selectedDate,
            onDismiss = { showMonthYearPicker = false },
            onConfirm = { newDate ->
                viewModel.setSelectedDate(newDate)
                showMonthYearPicker = false
            }
        )
    }

    if (showSelectionDialog) {
        AddTypeSelectionDialog(
            onDismiss = { showSelectionDialog = false },
            onSelect = { type ->
                showSelectionDialog = false
                showAddDialogForType = type
            }
        )
    }

    if (showAddDialogForType != null || editingTodo != null) {
        val currentType = when (editingTodo) {
            is ConsultationTodoEntity -> TodayPatientTab.CONSULTATION
            is VaccinationTodoEntity -> TodayPatientTab.VACCINATION
            else -> showAddDialogForType ?: TodayPatientTab.CONSULTATION
        }
        EnhancedAddTodoDialog(
            type = currentType,
            patients = uiState.patients,
            initialItem = editingTodo,
            allDoctors = uiState.allDoctors,
            slotsState = uiState.todoSlotsState,
            todoDate = selectedDate,
            onDoctorSelected = { doctorId -> viewModel.loadTodoSlots(doctorId, selectedDate) },
            onDismiss = { 
                showAddDialogForType = null
                editingTodo = null
                viewModel.clearTodoSlots()
            },
            onConfirm = { name, mobile, address, vaccineNames, patientId, doctorId, doctorName, slotId ->
                val id = when (editingTodo) {
                    is ConsultationTodoEntity -> (editingTodo as ConsultationTodoEntity).id
                    is VaccinationTodoEntity -> (editingTodo as VaccinationTodoEntity).id
                    else -> null
                }
                if (currentType == TodayPatientTab.CONSULTATION) {
                    viewModel.addConsultationDirect(
                        id = id, patientId = patientId, name = name, mobile = mobile, address = address,
                        doctorId = doctorId, doctorName = doctorName, availabilitySlotId = slotId
                    )
                } else {
                    viewModel.addVaccinationDirect(
                        id = id, patientId = patientId, name = name, mobile = mobile, address = address, vaccineNames = vaccineNames,
                        doctorId = doctorId, doctorName = doctorName, availabilitySlotId = slotId
                    )
                }
                showAddDialogForType = null
                editingTodo = null
                viewModel.clearTodoSlots()
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MonthYearPickerDialog(
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
private fun AddTypeSelectionDialog(
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
private fun EnhancedAddTodoDialog(
    type: TodayPatientTab,
    patients: List<Patient>,
    initialItem: Any? = null,
    allDoctors: List<com.neochildclinic.domain.model.Profile>,
    slotsState: com.neochildclinic.core.ui.SlotsUiState,
    todoDate: String,
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
    var selectedSlot by remember { mutableStateOf<com.neochildclinic.domain.model.AvailableSlot?>(null) }

    // Preselect the previously saved slot once availability has loaded for this doctor.
    LaunchedEffect(slotsState, initialItem) {
        val savedSlotId = when (initialItem) {
            is ConsultationTodoEntity -> initialItem.availabilitySlotId
            is VaccinationTodoEntity -> initialItem.availabilitySlotId
            else -> null
        }
        if (selectedSlot == null && !savedSlotId.isNullOrBlank()) {
            (slotsState as? com.neochildclinic.core.ui.SlotsUiState.Loaded)?.slots
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
                com.neochildclinic.core.ui.DoctorDropdown(
                    doctors = allDoctors,
                    selectedDoctor = selectedDoctor,
                    onDoctorSelected = { doctor ->
                        selectedDoctor = doctor
                        selectedSlot = null
                    }
                )

                if (selectedDoctor != null) {
                    com.neochildclinic.core.ui.AvailableSlotDropdown(
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

@Composable
private fun HorizontalDateSelector(
    selectedDate: String,
    datesWithData: Set<String>,
    onDateSelected: (String) -> Unit
) {
    val isoFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH) }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d", Locale.ENGLISH) }

    val daysInMonth = remember(selectedDate) {
        val selected = try {
            java.time.LocalDate.parse(selectedDate, isoFmt)
        } catch (_: java.time.format.DateTimeParseException) {
            java.time.LocalDate.now()
        }
        val currentMonth = selected.monthValue
        var cursor = selected.withDayOfMonth(1)

        val days = mutableListOf<java.time.LocalDate>()
        while (cursor.monthValue == currentMonth) {
            days.add(cursor)
            cursor = cursor.plusDays(1)
        }
        days
    }

    val listState = rememberLazyListState()
    
    LaunchedEffect(selectedDate) {
        val selectedIdx = daysInMonth.indexOfFirst { it.format(isoFmt) == selectedDate }
        if (selectedIdx >= 0) {
            listState.animateScrollToItem(selectedIdx)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(daysInMonth) { date ->
            val dateStr = date.format(isoFmt)
            val isSelected = dateStr == selectedDate
            val hasData = datesWithData.contains(dateStr)

            DateItem(
                dayName = date.format(dayFormat),
                dayDate = date.format(dateFormat),
                isSelected = isSelected,
                hasData = hasData,
                onClick = { onDateSelected(dateStr) }
            )
        }
    }
}

@Composable
private fun DateItem(
    dayName: String,
    dayDate: String,
    isSelected: Boolean,
    hasData: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(dayName, fontSize = 10.sp, fontWeight = FontWeight.Normal)
            Text(dayDate, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            // Selected date stays blue (above). Unselected dates with data get a green
            // dot; unselected dates without data get nothing (normal appearance).
            Box(
                modifier = Modifier.height(10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasData && !isSelected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(SuccessGreen, CircleShape)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayPatientItem(
    index: Int,
    item: Any,
    isHighlighted: Boolean = false,
    onStatusToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val status = when (item) {
        is ConsultationTodoEntity -> item.status
        is VaccinationTodoEntity -> item.status
        else -> "PENDING"
    }
    val isCompleted = status == "COMPLETED"
    
    val nameLine = when (item) {
        is ConsultationTodoEntity -> item.name
        is VaccinationTodoEntity -> "${item.name} (${item.vaccineNames})"
        else -> ""
    }
    val addressLine = when (item) {
        is ConsultationTodoEntity -> item.address
        is VaccinationTodoEntity -> item.address
        else -> ""
    }
    val phoneNumber = when (item) {
        is ConsultationTodoEntity -> item.mobile
        is VaccinationTodoEntity -> item.mobile
        else -> ""
    }

    val customColors = LocalCustomColors.current
    val color = if (item is ConsultationTodoEntity) customColors.softBlue else customColors.softGreen
    val textColor = if (item is ConsultationTodoEntity) customColors.textBlue else customColors.textGreen

    // Brief visual anchor for a notification-driven arrival (req. 9) - fades back to the
    // card's normal border once TodayPatientsScreen clears pendingHighlightId below.
    val highlightBorderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isHighlighted) textColor else Color.Transparent,
        label = "todayPatientHighlight"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isHighlighted) 2.dp else 0.dp,
                color = highlightBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = {},
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) color.copy(alpha = 0.5f) else color
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = if (isCompleted) 0.5f else 1f),
                modifier = Modifier.width(24.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = nameLine,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = if (isCompleted) 0.5f else 1f)
                )
                if (addressLine.isNotBlank()) {
                    Text(
                        text = addressLine,
                        fontSize = 13.sp,
                        color = textColor.copy(alpha = if (isCompleted) 0.3f else 0.7f)
                    )
                }
            }
            
            if (phoneNumber.isNotBlank()) {
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = textColor)
                }
            }

            IconButton(onClick = onStatusToggle) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isCompleted) "Mark Pending" else "Mark Completed",
                    tint = if (isCompleted) Color(0xFF4CAF50) else textColor
                )
            }
            
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}
