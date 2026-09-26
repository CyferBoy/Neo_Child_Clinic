package com.neochildclinic.features.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
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
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import java.time.format.DateTimeFormatter
import java.util.Locale

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