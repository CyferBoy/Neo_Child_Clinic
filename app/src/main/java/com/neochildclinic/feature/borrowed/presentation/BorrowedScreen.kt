package com.neochildclinic.feature.borrowed.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.ui.*
import com.neochildclinic.domain.model.BorrowedVaccine
import com.neochildclinic.domain.model.InventoryItem
import java.time.LocalDate

enum class BorrowFilter {
    OUTSTANDING,
    RETURNED,
    ALL
}

@Composable
fun BorrowedScreen(
    onBack: () -> Unit,
    viewModel: BorrowedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    ShowSnackbar(uiState.actionError, snackbarHostState, duration = SnackbarDuration.Short) {
        viewModel.clearActionError()
    }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<BorrowedDisplayItem?>(null) }
    var historyItem by remember { mutableStateOf<BorrowedDisplayItem?>(null) }
    var returningItem by remember { mutableStateOf<BorrowedDisplayItem?>(null) }
    var itemToDelete by remember { mutableStateOf<BorrowedDisplayItem?>(null) }

    BorrowedContent(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
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
        onDeleteRequest = { itemToDelete = it },
        snackbarHostState = snackbarHostState
    )

    if (showAddDialog) {
        BorrowedEditDialog(
            item = editingItem,
            defaultType = "FROM",
            inventory = uiState.inventory,
            onDismiss = { showAddDialog = false },
            onSave = {
                viewModel.saveBorrowedItem(it)
                showAddDialog = false
            }
        )
    }

    historyItem?.let { item ->
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

    DeleteConfirmationDialog(
        show = itemToDelete != null,
        onDismiss = { itemToDelete = null },
        onConfirm = {
            itemToDelete?.let { viewModel.deleteBorrowedItem(it.id) }
            itemToDelete = null
        },
        title = "Delete Borrowed Item",
        message = "Are you sure you want to delete this borrowed item record?"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BorrowedContent(
    uiState: BorrowedUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (BorrowedDisplayItem) -> Unit,
    onEditRequest: (BorrowedDisplayItem) -> Unit,
    onReturnRequest: (BorrowedDisplayItem) -> Unit,
    onDeleteRequest: (BorrowedDisplayItem) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val isDark = isSystemInDarkTheme()
    var currentFilter by rememberSaveable { mutableStateOf(BorrowFilter.OUTSTANDING) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }

    // Summary statistics matching SVG exactly:
    val activeLoans = remember(uiState.borrowedList) {
        uiState.borrowedList.count { it.status != BorrowStatus.RETURNED }
    }
    val dosesPending = remember(uiState.borrowedList) {
        uiState.borrowedList
            .filter { it.status != BorrowStatus.RETURNED }
            .sumOf { it.remainingQuantity }
    }
    val fullyReturned = remember(uiState.borrowedList) {
        uiState.borrowedList.count { it.status == BorrowStatus.RETURNED }
    }

    val filteredList = remember(uiState.borrowedList, currentFilter, searchQuery) {
        uiState.borrowedList
            .filter { item ->
                val matchesFilter = when (currentFilter) {
                    BorrowFilter.OUTSTANDING -> item.status != BorrowStatus.RETURNED
                    BorrowFilter.RETURNED -> item.status == BorrowStatus.RETURNED
                    BorrowFilter.ALL -> true
                }
                val matchesSearch = if (searchQuery.isBlank()) {
                    true
                } else {
                    item.vaccineName.contains(searchQuery, ignoreCase = true) ||
                    item.doctorName.contains(searchQuery, ignoreCase = true) ||
                    item.batchNumber.contains(searchQuery, ignoreCase = true) ||
                    (item.notes?.contains(searchQuery, ignoreCase = true) == true)
                }
                matchesFilter && matchesSearch
            }
            .sortedWith(
                when (currentFilter) {
                    BorrowFilter.OUTSTANDING -> compareByDescending<BorrowedDisplayItem> { item ->
                        isLoanOverdue(item.borrowedDate, item.remainingQuantity)
                    }.thenByDescending {
                        it.status == BorrowStatus.PARTIALLY_RETURNED
                    }.thenByDescending {
                        PatientUtils.parseDate(it.latestDate)?.time ?: 0L
                    }
                    BorrowFilter.RETURNED -> compareByDescending {
                        PatientUtils.parseDate(it.latestDate)?.time ?: 0L
                    }
                    BorrowFilter.ALL -> compareByDescending<BorrowedDisplayItem> {
                        it.status != BorrowStatus.RETURNED
                    }.thenByDescending {
                        PatientUtils.parseDate(it.latestDate)?.time ?: 0L
                    }
                }
            )
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Borrowed Vaccines",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchOpen = !isSearchOpen }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = {
                            currentFilter = if (currentFilter == BorrowFilter.RETURNED) {
                                BorrowFilter.OUTSTANDING
                            } else {
                                BorrowFilter.RETURNED
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onAddClick,
                    shape = CircleShape,
                    containerColor = if (isDark) Color(0xFF9CC7FF) else Color(0xFF16558F),
                    contentColor = if (isDark) Color(0xFF07305B) else Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Borrowed")
                }
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.screen),
                    contentPadding = PaddingValues(top = Spacing.xs, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Search bar
                    if (isSearchOpen) {
                        item {
                            StandardTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = "",
                                placeholder = "Search by vaccine, doctor or batch...",
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 1. Summary Stats Card (353 x 68dp card, rx 20)
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = if (isDark) Color(0xFF151F1B) else Color.White,
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF2C3A34) else Color(0xFFD8D2C6))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$activeLoans",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.02).sp,
                                        color = if (isDark) Color(0xFFEEF3F0) else Color(0xFF14201F)
                                    )
                                    Text(
                                        text = "active loans",
                                        fontSize = 9.5.sp,
                                        color = if (isDark) Color(0xFF9AA8A1) else Color(0xFF5F6D68)
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(40.dp),
                                    color = if (isDark) Color(0xFF2C3A34) else Color(0xFFD8D2C6)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$dosesPending",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.02).sp,
                                        color = if (isDark) Color(0xFFF5C15C) else Color(0xFF8A5300)
                                    )
                                    Text(
                                        text = "doses pending",
                                        fontSize = 9.5.sp,
                                        color = if (isDark) Color(0xFF9AA8A1) else Color(0xFF5F6D68)
                                    )
                                }

                                VerticalDivider(
                                    modifier = Modifier.height(40.dp),
                                    color = if (isDark) Color(0xFF2C3A34) else Color(0xFFD8D2C6)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$fullyReturned",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.02).sp,
                                        color = if (isDark) Color(0xFF8DE0AB) else Color(0xFF067647)
                                    )
                                    Text(
                                        text = "fully returned",
                                        fontSize = 9.5.sp,
                                        color = if (isDark) Color(0xFF9AA8A1) else Color(0xFF5F6D68)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Filter Pills (Outstanding, Returned, All)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(
                                BorrowFilter.OUTSTANDING to "Outstanding",
                                BorrowFilter.RETURNED to "Returned",
                                BorrowFilter.ALL to "All"
                            ).forEach { (filter, title) ->
                                val isSelected = currentFilter == filter
                                Surface(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { currentFilter = filter },
                                    shape = RoundedCornerShape(18.dp),
                                    color = when {
                                        isSelected -> if (isDark) Color(0xFF9CC7FF) else Color(0xFF16558F)
                                        else -> if (isDark) Color(0xFF151F1B) else Color.White
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isSelected -> if (isDark) Color(0xFF9CC7FF) else Color(0xFF16558F)
                                            else -> if (isDark) Color(0xFF2C3A34) else Color(0xFFD8D2C6)
                                        }
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isSelected -> if (isDark) Color(0xFF07305B) else Color.White
                                                else -> if (isDark) Color(0xFF9AA8A1) else Color(0xFF5F6D68)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Section Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = when (currentFilter) {
                                        BorrowFilter.OUTSTANDING -> "Outstanding"
                                        BorrowFilter.RETURNED -> "Returned"
                                        BorrowFilter.ALL -> "All"
                                    },
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFFEEF3F0) else Color(0xFF14201F)
                                )
                                Text(
                                    text = "Loans from other clinics and doctors",
                                    fontSize = 9.5.sp,
                                    color = if (isDark) Color(0xFF9AA8A1) else Color(0xFF5F6D68)
                                )
                            }

                            Text(
                                text = "Add borrowed",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFF9CC7FF) else Color(0xFF16558F),
                                modifier = Modifier.clickable { onAddClick() }
                            )
                        }
                    }

                    // 4. Cards or Empty State
                    if (uiState.isLoading && uiState.borrowedList.isEmpty()) {
                        item {
                            SkeletonList(
                                count = 4,
                                cardShaped = true,
                                spacing = Spacing.md
                            )
                        }
                    } else if (filteredList.isEmpty()) {
                        item {
                            EmptyState(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xl),
                                icon = Icons.Default.Inventory2,
                                title = if (currentFilter == BorrowFilter.RETURNED) "No returns yet" else "No borrowed vaccines",
                                message = if (currentFilter == BorrowFilter.RETURNED)
                                    "Doses returned to the clinic will be listed here."
                                else "Record a vaccine borrowed from or lent to another clinic to track it here."
                            )
                        }
                    } else {
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

                        // 5. Footer Note matching SVG
                        item {
                            Text(
                                text = "Returns reduce stock only after confirmation.",
                                fontSize = 9.5.sp,
                                color = if (isDark) Color(0xFF9AA8A1) else Color(0xFF5F6D68),
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
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
    var borrowedDate by rememberSaveable { mutableStateOf(item?.borrowedDate ?: LocalDate.now().toString()) }
    var quantity by rememberSaveable { mutableStateOf(item?.borrowedQuantity ?: 1) }
    var type by rememberSaveable { mutableStateOf(item?.type ?: defaultType) }

    var expanded by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        title = { Text(if (item == null) "Add Borrowed Vaccine" else "Edit Borrowed Vaccine", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
