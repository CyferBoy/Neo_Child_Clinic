package com.neochildclinic.features.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.net.Uri

private enum class TodayPatientTab { CONSULTATION, VACCINATION }

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, ExperimentalFoundationApi::class)
@Composable
fun TodayPatientsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(TodayPatientTab.CONSULTATION) }
    var showSelectionDialog by remember { mutableStateOf(false) }
    var showAddDialogForType by remember { mutableStateOf<TodayPatientTab?>(null) }
    var editingTodo by remember { mutableStateOf<Any?>(null) }
    var showMonthYearPicker by remember { mutableStateOf(false) }

    val pendingList = if (selectedTab == TodayPatientTab.CONSULTATION) uiState.todayConsultations else uiState.todayVaccinations
    val visitedList = if (selectedTab == TodayPatientTab.CONSULTATION) uiState.visitedConsultations else uiState.visitedVaccinations
    val customColors = LocalCustomColors.current

    val displayMonthYear = remember(selectedDate) {
        val calendar = Calendar.getInstance()
        calendar.time = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(selectedDate) ?: Date()
        SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(calendar.time)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(customColors.bgOffWhite)) {
                TopAppBar(
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
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
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

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
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
                        TodayPatientItem(
                            index = index + 1,
                            item = item,
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
                            TodayPatientItem(
                                index = index + 1,
                                item = item,
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
            onDismiss = { 
                showAddDialogForType = null
                editingTodo = null
            },
            onConfirm = { name, mobile, address, vaccineNames, patientId ->
                val id = when (editingTodo) {
                    is ConsultationTodoEntity -> (editingTodo as ConsultationTodoEntity).id
                    is VaccinationTodoEntity -> (editingTodo as VaccinationTodoEntity).id
                    else -> null
                }
                if (currentType == TodayPatientTab.CONSULTATION) {
                    viewModel.addConsultationDirect(id = id, patientId = patientId, name = name, mobile = mobile, address = address)
                } else {
                    viewModel.addVaccinationDirect(id = id, patientId = patientId, name = name, mobile = mobile, address = address, vaccineNames = vaccineNames)
                }
                showAddDialogForType = null
                editingTodo = null
            }
        )
    }
}

@Composable
private fun MonthYearPickerDialog(
    currentDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    val calendar = remember {
        Calendar.getInstance().apply {
            time = sdf.parse(currentDate) ?: Date()
        }
    }

    var selectedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var selectedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }

    val months = remember {
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    }
    val years = remember { (2020..2030).toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Month & Year") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var monthExpanded by remember { mutableStateOf(false) }
                var yearExpanded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.weight(1.5f)) {
                    OutlinedButton(onClick = { monthExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(months[selectedMonth])
                    }
                    DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                        months.forEachIndexed { index, name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { selectedMonth = index; monthExpanded = false })
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { yearExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedYear.toString())
                    }
                    DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                        years.forEach { year ->
                            DropdownMenuItem(text = { Text(year.toString()) }, onClick = { selectedYear = year; yearExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newCal = Calendar.getInstance()
                newCal.set(Calendar.YEAR, selectedYear)
                newCal.set(Calendar.MONTH, selectedMonth)
                newCal.set(Calendar.DAY_OF_MONTH, 1)
                onConfirm(sdf.format(newCal.time))
            }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String?) -> Unit
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
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && mobile.isNotBlank() && (type == TodayPatientTab.CONSULTATION || vaccineNames.isNotBlank()),
                onClick = { onConfirm(name, mobile, address, vaccineNames, selectedPatientId) }
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
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }
    val dateFormat = remember { SimpleDateFormat("d", Locale.ENGLISH) }

    val daysInMonth = remember(selectedDate) {
        val calendar = Calendar.getInstance()
        calendar.time = sdf.parse(selectedDate) ?: Date()
        val currentMonth = calendar.get(Calendar.MONTH)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        
        val days = mutableListOf<Date>()
        while (calendar.get(Calendar.MONTH) == currentMonth) {
            days.add(calendar.time)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        days
    }

    val listState = rememberLazyListState()
    
    LaunchedEffect(selectedDate) {
        val selectedIdx = daysInMonth.indexOfFirst { sdf.format(it) == selectedDate }
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
            val dateStr = sdf.format(date)
            val isSelected = dateStr == selectedDate
            val hasData = datesWithData.contains(dateStr)

            DateItem(
                dayName = dayFormat.format(date),
                dayDate = dateFormat.format(date),
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
            if (!hasData) {
                Text("x", fontSize = 10.sp, color = if (isSelected) Color.White.copy(alpha = 0.7f) else Color.Red)
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayPatientItem(
    index: Int,
    item: Any,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
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
