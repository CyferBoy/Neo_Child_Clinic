package com.neochildclinic.features.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.model.BorrowedVaccine
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.core.ui.StandardAutoCompleteField
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.designsystem.NeoChildTheme
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
    var editingItem by remember { mutableStateOf<BorrowedVaccine?>(null) }

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
        onReturnRequest = viewModel::markAsReturned,
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
    filteredList: List<BorrowedVaccine>,
    onBack: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onAddClick: () -> Unit,
    onEditRequest: (BorrowedVaccine) -> Unit,
    onReturnRequest: (BorrowedVaccine) -> Unit,
    onDeleteRequest: (BorrowedVaccine) -> Unit
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                    contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        BorrowedItemCard(
                            item = item,
                            onEdit = { onEditRequest(item) },
                            onReturn = { onReturnRequest(item) },
                            onDelete = { onDeleteRequest(item) },
                            modifier = Modifier.animateItem()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BorrowedItemCard(
    item: BorrowedVaccine,
    onEdit: () -> Unit,
    onReturn: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = { menuExpanded = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isReturned) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                             else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.doctorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (item.isReturned) {
                        Text("RETURNED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    }
                }
                Text("Vaccine: ${item.vaccineName}", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Date: ${formatDateForDisplay(item.borrowedDate)}", style = MaterialTheme.typography.bodySmall)
                    Text("Batch: ${item.batchNumber}", style = MaterialTheme.typography.bodySmall)
                }
                Text("Expiry: ${formatDateForDisplay(item.expiryDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuExpanded = false; onEdit() })
                    if (!item.isReturned) {
                        DropdownMenuItem(text = { Text("Mark as Returned") }, onClick = { menuExpanded = false; onReturn() })
                    }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
fun BorrowedEditDialog(
    item: BorrowedVaccine?,
    defaultType: String,
    inventory: List<InventoryItem>,
    onDismiss: () -> Unit,
    onSave: (BorrowedVaccine) -> Unit
) {
    var doctorName by rememberSaveable { mutableStateOf(item?.doctorName ?: "") }
    var vaccineName by rememberSaveable { mutableStateOf(item?.vaccineName ?: "") }
    var borrowedDate by rememberSaveable { mutableStateOf(item?.borrowedDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())) }
    var batchNumber by rememberSaveable { mutableStateOf(item?.batchNumber ?: "") }
    var expiryDate by rememberSaveable { mutableStateOf(item?.expiryDate ?: "") }
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
                
                val suggestions = remember(vaccineName, inventory) {
                    inventory.filter { it.brandName.contains(vaccineName, ignoreCase = true) || it.type.contains(vaccineName, ignoreCase = true) }
                }
                
                StandardAutoCompleteField(
                    value = vaccineName,
                    onValueChange = { newValue -> 
                        vaccineName = newValue
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
                                    vaccineName = v.brandName
                                    val firstBatch = v.batches.firstOrNull()
                                    batchNumber = firstBatch?.batchNumber ?: ""
                                    expiryDate = firstBatch?.expiryDate ?: ""
                                    expanded = false
                                }
                            )
                        }
                    }
                )

                StandardTextField(value = borrowedDate, onValueChange = { borrowedDate = it }, label = "Date (yyyy-MM-dd)", modifier = Modifier.fillMaxWidth())
                StandardTextField(value = batchNumber, onValueChange = { batchNumber = it }, label = "Batch Number", modifier = Modifier.fillMaxWidth())
                StandardTextField(value = expiryDate, onValueChange = { expiryDate = it }, label = "Expiry Date (yyyy-MM-dd)", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            StandardButton(onClick = {
                onSave(BorrowedVaccine(
                    id = item?.id ?: "",
                    doctorName = doctorName,
                    vaccineName = vaccineName,
                    borrowedDate = borrowedDate,
                    batchNumber = batchNumber,
                    expiryDate = expiryDate,
                    isReturned = item?.isReturned ?: false,
                    returnedDate = item?.returnedDate,
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

@Preview(showBackground = true)
@Composable
private fun BorrowedPreview() {
    NeoChildTheme {
        BorrowedContent(
            uiState = BorrowedUiState(isLoading = false, selectedTab = 0),
            filteredList = listOf(BorrowedVaccine("1", "Dr. Hassan", "2024-01-01", "BCG", "2025-01-01", "B123", false, null, "BY")),
            onBack = {},
            onTabSelected = {},
            onAddClick = {},
            onEditRequest = {},
            onReturnRequest = {},
            onDeleteRequest = {}
        )
    }
}
