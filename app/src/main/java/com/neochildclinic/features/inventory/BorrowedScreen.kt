package com.neochildclinic.features.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.model.BorrowedVaccine
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.core.ui.StandardAutoCompleteField
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BorrowedScreen(
    onBack: () -> Unit,
    viewModel: BorrowedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BorrowedDisplayItem?>(null) }

    val filteredList = remember(uiState.borrowedList, uiState.selectedTab) {
        val type = if (uiState.selectedTab == 0) "BY" else "FROM"
        uiState.borrowedList.filter { it.type == type }
    }

    BorrowedContent(
        uiState = uiState,
        filteredList = filteredList,
        onBack = onBack,
        onTabSelected = viewModel::updateTab,
        onAddClick = {
            editingItem = null
            showAddDialog = true
        },
        onEditRequest = { item ->
            editingItem = item
            showAddDialog = true
        },
        onReturnRequest = { viewModel.markAsReturned(it.record) },
        onDeleteRequest = { viewModel.deleteBorrowedItem(it.id) }
    )

    if (showAddDialog) {
        BorrowedEditDialog(
            item = editingItem,
            defaultType = if (uiState.selectedTab == 0) "BY" else "FROM",
            inventory = uiState.inventory,
            onDismiss = { showAddDialog = false },
            onSave = {
                viewModel.saveBorrowedItem(it)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BorrowedContent(
    uiState: BorrowedUiState,
    filteredList: List<BorrowedDisplayItem>,
    onBack: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onAddClick: () -> Unit,
    onEditRequest: (BorrowedDisplayItem) -> Unit,
    onReturnRequest: (BorrowedDisplayItem) -> Unit,
    onDeleteRequest: (BorrowedDisplayItem) -> Unit
) {
    val tabs = remember { listOf("By", "From") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Borrowed Vaccines") },
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
                TabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = uiState.selectedTab == index,
                            onClick = { onTabSelected(index) },
                            text = { Text(title, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Borrowed")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.isLoading && uiState.borrowedList.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (filteredList.isEmpty()) {
                Text(
                    text = "No records found.", 
                    modifier = Modifier.align(Alignment.Center), 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        BorrowedRecordCard(
                            item = item,
                            onEdit = { onEditRequest(item) },
                            onReturn = { onReturnRequest(item) },
                            onDelete = { onDeleteRequest(item) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BorrowedEditDialog(
    item: BorrowedDisplayItem?,
    defaultType: String,
    inventory: List<InventoryItem>,
    onDismiss: () -> Unit,
    onSave: (BorrowedVaccine) -> Unit
) {
    var doctorName by rememberSaveable { mutableStateOf(item?.doctorName ?: "") }
    var vaccineId by rememberSaveable { mutableStateOf(item?.record?.vaccineId ?: "") }
    var vaccineSearch by rememberSaveable { mutableStateOf(item?.vaccineName ?: "") }
    var batchId by rememberSaveable { mutableStateOf(item?.record?.batchId ?: "") }
    var batchNumber by rememberSaveable { mutableStateOf(item?.batchNumber ?: "") }
    var borrowedDate by rememberSaveable { mutableStateOf(item?.borrowedDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())) }
    var quantity by rememberSaveable { mutableStateOf(item?.quantity ?: 1) }
    var type by rememberSaveable { mutableStateOf(item?.type ?: defaultType) }
    
    var expanded by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add Borrowed Vaccine" else "Edit Borrowed Vaccine") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "BY",
                        onClick = { type = "BY" },
                        label = { Text("Borrowed By") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == "FROM",
                        onClick = { type = "FROM" },
                        label = { Text("Borrowed From") },
                        modifier = Modifier.weight(1f)
                    )
                }

                StandardTextField(
                    value = doctorName, 
                    onValueChange = { doctorName = it }, 
                    label = if (type == "BY") "Doctor Name" else "Source/Doctor Name", 
                    modifier = Modifier.fillMaxWidth()
                )
                
                val suggestions = remember(vaccineSearch, inventory) {
                    inventory.filter { it.brandName.contains(vaccineSearch, ignoreCase = true) || it.type.contains(vaccineSearch, ignoreCase = true) }
                }
                
                StandardAutoCompleteField(
                    value = vaccineSearch,
                    onValueChange = { newValue -> 
                        vaccineSearch = newValue
                        expanded = true
                    },
                    label = "Vaccine Name",
                    placeholder = "Search inventory...",
                    expanded = expanded && suggestions.isNotEmpty(),
                    onExpandedChange = { expanded = it },
                    dropdownContent = {
                        suggestions.forEach { v ->
                            DropdownMenuItem(
                                text = { Text("${v.brandName} (${v.type})") },
                                onClick = {
                                    vaccineSearch = v.brandName
                                    vaccineId = v.id
                                    val firstBatch = v.batches.firstOrNull()
                                    batchId = firstBatch?.batchId ?: ""
                                    batchNumber = firstBatch?.batchNumber ?: ""
                                    expanded = false
                                }
                            )
                        }
                    }
                )

                StandardTextField(value = borrowedDate, onValueChange = { borrowedDate = it }, label = "Date (yyyy-MM-dd)", modifier = Modifier.fillMaxWidth())
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(value = batchNumber, onValueChange = { batchNumber = it }, label = "Batch Number", modifier = Modifier.weight(1f), enabled = false)
                    StandardTextField(value = quantity.toString(), onValueChange = { quantity = it.toIntOrNull() ?: 1 }, label = "Qty", modifier = Modifier.weight(0.5f))
                }
            }
        },
        confirmButton = {
            StandardButton(onClick = {
                if (vaccineId.isEmpty() || batchId.isEmpty()) return@StandardButton
                
                onSave(BorrowedVaccine(
                    id = item?.id ?: "",
                    doctorName = doctorName,
                    vaccineId = vaccineId,
                    batchId = batchId,
                    borrowedDate = borrowedDate,
                    quantity = quantity,
                    isReturned = item?.record?.isReturned ?: false,
                    returnedDate = item?.record?.returnedDate,
                    type = type
                ))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
