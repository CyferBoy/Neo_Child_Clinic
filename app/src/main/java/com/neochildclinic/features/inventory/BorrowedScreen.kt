package com.neochildclinic.features.inventory

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
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.core.ui.StandardAutoCompleteField
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
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
    var historyItem by remember { mutableStateOf<BorrowedDisplayItem?>(null) }
    var returningItem by remember { mutableStateOf<BorrowedDisplayItem?>(null) }

    // Outer Borrowed/Returned split, then the By/From type filter, then sort so
    // records needing attention (partially returned) surface first while still
    // reading latest-date-first overall - never plain alphabetical.
    val filteredList = remember(uiState.borrowedList, uiState.mainTab, uiState.selectedTab) {
        val byMainTab = uiState.borrowedList.filter { item ->
            when (uiState.mainTab) {
                BorrowMainTab.BORROWED -> item.status != BorrowStatus.RETURNED
                BorrowMainTab.RETURNED -> item.status == BorrowStatus.RETURNED
            }
        }
        val typeValue = if (uiState.selectedTab == 0) "BY" else "FROM"
        val byType = byMainTab.filter { it.type.equals(typeValue, ignoreCase = true) }

        when (uiState.mainTab) {
            BorrowMainTab.BORROWED -> byType.sortedWith(
                compareByDescending<BorrowedDisplayItem> { it.status == BorrowStatus.PARTIALLY_RETURNED }
                    .thenByDescending { PatientUtils.parseDate(it.latestDate)?.time ?: 0L }
            )
            BorrowMainTab.RETURNED -> byType.sortedByDescending { PatientUtils.parseDate(it.latestDate)?.time ?: 0L }
        }
    }

    BorrowedContent(
        uiState = uiState,
        filteredList = filteredList,
        onBack = onBack,
        onMainTabSelected = viewModel::selectMainTab,
        onTypeTabSelected = viewModel::updateTab,
        onAddClick = {
            editingItem = null
            showAddDialog = true
        },
        onItemClick = { historyItem = it },
        onEditRequest = { item ->
            editingItem = item
            showAddDialog = true
        },
        onReturnRequest = { returningItem = it },
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

    historyItem?.let { item ->
        // Keep the sheet showing the latest state of the item it's open for.
        val latest = uiState.borrowedList.find { it.id == item.id } ?: item
        BorrowHistorySheet(
            item = latest,
            onDismiss = { historyItem = null },
            onMarkAsReturned = {
                historyItem = null
                returningItem = latest
            }
        )
    }

    returningItem?.let { item ->
        val latest = uiState.borrowedList.find { it.id == item.id } ?: item
        ReturnVaccineDialog(
            item = latest,
            inventory = uiState.inventory,
            onDismiss = { returningItem = null },
            onConfirm = { quantity, batchId, notes, newBatchInfo ->
                viewModel.submitReturn(latest, quantity, batchId, notes, newBatchInfo)
                returningItem = null
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
    onMainTabSelected: (BorrowMainTab) -> Unit,
    onTypeTabSelected: (Int) -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (BorrowedDisplayItem) -> Unit,
    onEditRequest: (BorrowedDisplayItem) -> Unit,
    onReturnRequest: (BorrowedDisplayItem) -> Unit,
    onDeleteRequest: (BorrowedDisplayItem) -> Unit
) {
    val mainTabs = remember { listOf(BorrowMainTab.BORROWED to "Borrowed", BorrowMainTab.RETURNED to "Returned") }
    val mainTabIndex = mainTabs.indexOfFirst { it.first == uiState.mainTab }.coerceAtLeast(0)

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
                    selectedTabIndex = mainTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[mainTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    mainTabs.forEachIndexed { index, (tab, title) ->
                        Tab(
                            selected = mainTabIndex == index,
                            onClick = { onMainTabSelected(tab) },
                            text = { 
                                Text(
                                    text = title, 
                                    fontWeight = FontWeight.Bold,
                                    color = if (mainTabIndex == index) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            }
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
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.selectedTab == 0,
                        onClick = { onTypeTabSelected(0) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("By") }
                    SegmentedButton(
                        selected = uiState.selectedTab == 1,
                        onClick = { onTypeTabSelected(1) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("From") }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading && uiState.borrowedList.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (filteredList.isEmpty()) {
                    Text(
                        text = if (uiState.mainTab == BorrowMainTab.RETURNED) "No fully returned records yet." else "No records found.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredList, key = { it.id }) { item ->
                            BorrowedRecordCard(
                                item = item,
                                onClick = { onItemClick(item) },
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
    var quantity by rememberSaveable { mutableStateOf(item?.borrowedQuantity ?: 1) }
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
                    StandardTextField(
                        value = quantity.toString(),
                        onValueChange = { quantity = it.toIntOrNull() ?: 1 },
                        label = "Qty",
                        modifier = Modifier.weight(0.5f),
                        // Once any return has been recorded against this borrow, the
                        // borrowed quantity is locked - lowering it below what's already
                        // been returned would corrupt the returned/remaining math and
                        // violate "never overwrite the original borrowed quantity".
                        enabled = (item?.returnedQuantity ?: 0) == 0
                    )
                }
                if ((item?.returnedQuantity ?: 0) > 0) {
                    Text(
                        "Quantity is locked because ${item?.returnedQuantity} unit(s) have already been returned against this record.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
