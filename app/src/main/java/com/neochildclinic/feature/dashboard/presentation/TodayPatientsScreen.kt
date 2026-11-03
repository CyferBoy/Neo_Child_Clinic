package com.neochildclinic.feature.dashboard.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.designsystem.Spacing
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.EmptyState
import com.neochildclinic.domain.model.ConsultationTodo
import com.neochildclinic.domain.model.VaccinationTodo
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalFoundationApi::class)
@Composable
fun TodayPatientsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit,
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
    // New/Follow-up sub-tab (only active when Consultation is selected)
    var consultSubTab by rememberSaveable { mutableStateOf(ConsultSubTab.NEW) }

    var showSelectionDialog by remember { mutableStateOf(false) }
    var showAddDialogForType by remember { mutableStateOf<TodayPatientTab?>(null) }
    var editingTodo by remember { mutableStateOf<Any?>(null) }
    var showMonthYearPicker by remember { mutableStateOf(false) }
    var pendingHighlightId by remember(highlightId) { mutableStateOf(highlightId) }

    // Apply sub-tab filter to consultation lists; vaccination is unfiltered
    val pendingList = when {
        selectedTab == TodayPatientTab.VACCINATION -> uiState.todayVaccinations
        consultSubTab == ConsultSubTab.NEW -> uiState.todayConsultations.filter { it.visitType == "NEW" || it.visitType.isBlank() }
        else -> uiState.todayConsultations.filter { it.visitType == "FOLLOW_UP" }
    }
    val visitedList = when {
        selectedTab == TodayPatientTab.VACCINATION -> uiState.visitedVaccinations
        consultSubTab == ConsultSubTab.NEW -> uiState.visitedConsultations.filter { it.visitType == "NEW" || it.visitType.isBlank() }
        else -> uiState.visitedConsultations.filter { it.visitType == "FOLLOW_UP" }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(pendingHighlightId, pendingList, visitedList) {
        val targetId = pendingHighlightId ?: return@LaunchedEffect
        val pendingIndex = pendingList.indexOfFirst { item ->
            when (item) {
                is ConsultationTodo -> item.id == targetId
                is VaccinationTodo -> item.id == targetId
                else -> false
            }
        }
        val found = if (pendingIndex >= 0) {
            listState.animateScrollToItem(pendingIndex)
            true
        } else {
            val visitedIndex = visitedList.indexOfFirst { item ->
                when (item) {
                    is ConsultationTodo -> item.id == targetId
                    is VaccinationTodo -> item.id == targetId
                    else -> false
                }
            }
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
            java.time.LocalDate.parse(selectedDate)
        } catch (_: java.time.format.DateTimeParseException) {
            java.time.LocalDate.now()
        }
        date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(modifier = Modifier.background(Color.Transparent)) {
                    BackTopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Today's Patients",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Text(
                                    text = displayMonthYear,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clickable { showMonthYearPicker = true }
                                )
                            }
                        },
                        onBack = onBack,
                        actions = {
                            IconButton(onClick = { showMonthYearPicker = true }) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Change Month")
                            }
                            IconButton(onClick = {
                                editingTodo = null
                                showSelectionDialog = true
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Patient")
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingTodo = null
                        showSelectionDialog = true
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Patient Entry")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg)
            ) {
                // Main segmented control: Consultation | Vaccination with counts
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedTab = TodayPatientTab.CONSULTATION },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedTab == TodayPatientTab.CONSULTATION) MaterialTheme.colorScheme.surface else Color.Transparent,
                            border = if (selectedTab == TodayPatientTab.CONSULTATION) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                            shadowElevation = if (selectedTab == TodayPatientTab.CONSULTATION) 1.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Consultation (${uiState.consultationCount})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == TodayPatientTab.CONSULTATION) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == TodayPatientTab.CONSULTATION) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedTab = TodayPatientTab.VACCINATION },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedTab == TodayPatientTab.VACCINATION) MaterialTheme.colorScheme.surface else Color.Transparent,
                            border = if (selectedTab == TodayPatientTab.VACCINATION) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                            shadowElevation = if (selectedTab == TodayPatientTab.VACCINATION) 1.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Vaccination (${uiState.vaccinationCount})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == TodayPatientTab.VACCINATION) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == TodayPatientTab.VACCINATION) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Sub-segmented control: New | Follow-up — only visible when Consultation tab is active
                if (selectedTab == TodayPatientTab.CONSULTATION) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { consultSubTab = ConsultSubTab.NEW },
                                shape = RoundedCornerShape(8.dp),
                                color = if (consultSubTab == ConsultSubTab.NEW) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (consultSubTab == ConsultSubTab.NEW) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                                shadowElevation = if (consultSubTab == ConsultSubTab.NEW) 1.dp else 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "New (${uiState.newConsultCount})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (consultSubTab == ConsultSubTab.NEW) FontWeight.Bold else FontWeight.Normal,
                                        color = if (consultSubTab == ConsultSubTab.NEW) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { consultSubTab = ConsultSubTab.FOLLOW_UP },
                                shape = RoundedCornerShape(8.dp),
                                color = if (consultSubTab == ConsultSubTab.FOLLOW_UP) MaterialTheme.colorScheme.surface else Color.Transparent,
                                border = if (consultSubTab == ConsultSubTab.FOLLOW_UP) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                                shadowElevation = if (consultSubTab == ConsultSubTab.FOLLOW_UP) 1.dp else 0.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Follow-up (${uiState.followUpConsultCount})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (consultSubTab == ConsultSubTab.FOLLOW_UP) FontWeight.Bold else FontWeight.Normal,
                                        color = if (consultSubTab == ConsultSubTab.FOLLOW_UP) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Horizontal Day Tiles
                HorizontalDateSelector(
                    selectedDate = selectedDate,
                    datesWithData = uiState.datesWithData,
                    onDateSelected = { viewModel.setSelectedDate(it) }
                )

                // Dynamic slot filter
                if (uiState.slotSegments.size >= 2) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    SingleChoiceSegmentedButtonRow(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        uiState.slotSegments.forEachIndexed { index, segment ->
                            SegmentedButton(
                                selected = uiState.selectedSlotKey == segment.key,
                                onClick = { viewModel.setSelectedSlot(segment.key) },
                                shape = SegmentedButtonDefaults.itemShape(index, uiState.slotSegments.size),
                                modifier = Modifier.weight(1f)
                            ) { Text(segment.label) }
                        }
                    }
                }

                val totalCount = pendingList.size + visitedList.size
                val tabName = if (selectedTab == TodayPatientTab.CONSULTATION) "consultations" else "vaccinations"
                val dateFormatted = remember(selectedDate) {
                    try {
                        val d = java.time.LocalDate.parse(selectedDate)
                        d.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH))
                    } catch (_: Exception) {
                        selectedDate
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalCount $tabName · $dateFormatted",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Sorted by time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                AppPullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        contentPadding = PaddingValues(bottom = 80.dp, top = Spacing.xs)
                    ) {
                        if (pendingList.isEmpty() && visitedList.isEmpty()) {
                            item {
                                EmptyState(
                                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                    icon = Icons.Default.EventAvailable,
                                    title = "No $tabName for this date",
                                    message = "Tap + to add a patient to today's queue."
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = pendingList,
                                key = { _, item ->
                                    when (item) {
                                        is ConsultationTodo -> "c_${item.id}"
                                        is VaccinationTodo -> "v_${item.id}"
                                        else -> item.hashCode()
                                    }
                                }
                            ) { index, item ->
                                val itemId = when (item) {
                                    is ConsultationTodo -> item.id
                                    is VaccinationTodo -> item.id
                                    else -> null
                                }
                                val slotId = when (item) {
                                    is ConsultationTodo -> item.availabilitySlotId
                                    is VaccinationTodo -> item.availabilitySlotId
                                    else -> null
                                }
                                val slotRange = slotId?.let { uiState.slotRanges[it] }
                                TodayPatientItem(
                                    index = index + 1,
                                    item = item,
                                    slotRange = slotRange,
                                    isHighlighted = itemId != null && itemId == pendingHighlightId,
                                    onStatusToggle = { viewModel.toggleTodoStatus(item) },
                                    onDelete = {
                                        when (item) {
                                            is ConsultationTodo -> viewModel.deleteConsultation(item.id)
                                            is VaccinationTodo -> viewModel.deleteVaccination(item.id)
                                        }
                                    },
                                    onEdit = { editingTodo = item }
                                )
                            }

                            if (visitedList.isNotEmpty()) {
                                item {
                                    Text(
                                        "Visited",
                                        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                itemsIndexed(
                                    items = visitedList,
                                    key = { _, item ->
                                        when (item) {
                                            is ConsultationTodo -> "cv_${item.id}"
                                            is VaccinationTodo -> "vv_${item.id}"
                                            else -> item.hashCode()
                                        }
                                    }
                                ) { index, item ->
                                    val itemId = when (item) {
                                        is ConsultationTodo -> item.id
                                        is VaccinationTodo -> item.id
                                        else -> null
                                    }
                                    val slotId = when (item) {
                                        is ConsultationTodo -> item.availabilitySlotId
                                        is VaccinationTodo -> item.availabilitySlotId
                                        else -> null
                                    }
                                    val slotRange = slotId?.let { uiState.slotRanges[it] }
                                    TodayPatientItem(
                                        index = pendingList.size + index + 1,
                                        item = item,
                                        slotRange = slotRange,
                                        isHighlighted = itemId != null && itemId == pendingHighlightId,
                                        onStatusToggle = { viewModel.toggleTodoStatus(item) },
                                        onDelete = {
                                            when (item) {
                                                is ConsultationTodo -> viewModel.deleteConsultation(item.id)
                                                is VaccinationTodo -> viewModel.deleteVaccination(item.id)
                                            }
                                        },
                                        onEdit = { editingTodo = item }
                                    )
                                }
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Showing $totalCount of $totalCount · scroll for more",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
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
            is ConsultationTodo -> TodayPatientTab.CONSULTATION
            is VaccinationTodo -> TodayPatientTab.VACCINATION
            else -> showAddDialogForType ?: TodayPatientTab.CONSULTATION
        }
        // Pre-fill visitType from sub-tab when adding a new consultation
        val defaultVisitType = if (currentType == TodayPatientTab.CONSULTATION && editingTodo == null) {
            when (consultSubTab) {
                ConsultSubTab.FOLLOW_UP -> "FOLLOW_UP"
                else -> "NEW"
            }
        } else ""

        EnhancedAddTodoDialog(
            type = currentType,
            patients = uiState.patients,
            initialItem = editingTodo,
            defaultVisitType = defaultVisitType,
            allDoctors = uiState.allDoctors,
            slotsState = uiState.todoSlotsState,
            onDoctorSelected = { doctorId -> viewModel.loadTodoSlots(doctorId, selectedDate) },
            onDismiss = {
                showAddDialogForType = null
                editingTodo = null
                viewModel.clearTodoSlots()
            },
            onConfirm = { name, mobile, address, vaccineNames, patientId, doctorId, doctorName, slotId, visitType ->
                val id = when (editingTodo) {
                    is ConsultationTodo -> (editingTodo as ConsultationTodo).id
                    is VaccinationTodo -> (editingTodo as VaccinationTodo).id
                    else -> null
                }
                if (currentType == TodayPatientTab.CONSULTATION) {
                    viewModel.addConsultationDirect(
                        id = id, patientId = patientId, name = name, mobile = mobile, address = address,
                        doctorId = doctorId, doctorName = doctorName, availabilitySlotId = slotId,
                        visitType = visitType
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
