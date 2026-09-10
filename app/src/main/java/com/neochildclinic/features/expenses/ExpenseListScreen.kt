package com.neochildclinic.features.expenses

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.DeleteConfirmationDialog
import com.neochildclinic.core.ui.SearchTopAppBar
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.ExpenseCategory
import com.neochildclinic.domain.model.ExpensePaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onBack: () -> Unit = {},
    onAddExpense: () -> Unit = {},
    onEditExpense: (String) -> Unit = {},
    viewModel: ExpenseListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isSearchActive by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    LaunchedEffect(uiState.deletedMessage) {
        uiState.deletedMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearDeletedMessage()
        }
    }

    val filtersActive = uiState.categoryFilter != null || uiState.paymentMethodFilter != null ||
        uiState.fromDate.isNotBlank() || uiState.toDate.isNotBlank()

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SearchTopAppBar(
                    title = "Expenses",
                    searchQuery = uiState.query,
                    onSearchQueryChange = viewModel::onQueryChange,
                    isSearchActive = isSearchActive,
                    onSearchActiveChange = { isSearchActive = it },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                if (filtersActive) Icons.Default.FilterAlt else Icons.Default.FilterAltOff,
                                contentDescription = "Filters",
                                tint = if (filtersActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddExpense) {
                    Icon(Icons.Default.Add, "Add Expense")
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (showFilters) {
                    ExpenseFilters(uiState = uiState, viewModel = viewModel)
                }

                AppPullToRefresh(isRefreshing = uiState.isLoading, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                    if (!uiState.isLoading && uiState.expenses.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (filtersActive || uiState.query.isNotBlank()) "No expenses match these filters" else "No expenses recorded yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.expenses, key = { it.id }) { expense ->
                                ExpenseCard(expense = expense, onClick = { selectedExpense = expense })
                            }
                            if (uiState.canLoadMore) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                        if (uiState.isLoadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                        } else {
                                            TextButton(onClick = viewModel::loadMore) { Text("Load More") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedExpense?.let { expense ->
        ExpenseDetailSheet(
            expense = expense,
            canManage = uiState.canManage,
            onDismiss = { selectedExpense = null },
            onEdit = {
                selectedExpense = null
                onEditExpense(expense.id)
            },
            onDelete = {
                expenseToDelete = expense
                selectedExpense = null
            }
        )
    }

    DeleteConfirmationDialog(
        show = expenseToDelete != null,
        onDismiss = { expenseToDelete = null },
        onConfirm = {
            expenseToDelete?.let { viewModel.deleteExpense(it.id) }
            expenseToDelete = null
        },
        title = "Delete Expense",
        message = "Are you sure you want to delete \"${expenseToDelete?.title}\"? This action cannot be undone."
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseFilters(uiState: ExpenseListUiState, viewModel: ExpenseListViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var categoryExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = uiState.categoryFilter?.label ?: "All Categories",
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                DropdownMenuItem(text = { Text("All Categories") }, onClick = { viewModel.onCategoryFilterChange(null); categoryExpanded = false })
                ExpenseCategory.entries.forEach { category ->
                    DropdownMenuItem(text = { Text(category.label) }, onClick = { viewModel.onCategoryFilterChange(category); categoryExpanded = false })
                }
            }
        }

        var paymentExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = paymentExpanded, onExpandedChange = { paymentExpanded = !paymentExpanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = uiState.paymentMethodFilter?.label ?: "All Payment Methods",
                onValueChange = {},
                readOnly = true,
                label = { Text("Payment Method") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paymentExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = paymentExpanded, onDismissRequest = { paymentExpanded = false }) {
                DropdownMenuItem(text = { Text("All Payment Methods") }, onClick = { viewModel.onPaymentMethodFilterChange(null); paymentExpanded = false })
                ExpensePaymentMethod.entries.forEach { method ->
                    DropdownMenuItem(text = { Text(method.label) }, onClick = { viewModel.onPaymentMethodFilterChange(method); paymentExpanded = false })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateDropdownPicker(
                label = "From",
                currentDate = uiState.fromDate,
                onDateSelected = { viewModel.onDateRangeChange(it, uiState.toDate) },
                modifier = Modifier.weight(1f)
            )
            DateDropdownPicker(
                label = "To",
                currentDate = uiState.toDate,
                onDateSelected = { viewModel.onDateRangeChange(uiState.fromDate, it) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpenseSortOption.entries.forEach { option ->
                FilterChip(
                    selected = uiState.sort == option,
                    onClick = { viewModel.onSortChange(option) },
                    label = { Text(option.label) }
                )
            }
        }

        TextButton(onClick = viewModel::clearFilters, modifier = Modifier.align(Alignment.End)) {
            Text("Clear Filters")
        }
        HorizontalDivider()
    }
}

@Composable
private fun ExpenseCard(expense: Expense, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(expense.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "\u20B9${"%.2f".format(expense.amountRupees)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(PatientUtils.formatDateForDisplay(expense.expenseDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("\u2022", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(expense.paymentMethod.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailSheet(
    expense: Expense,
    canManage: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(expense.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "\u20B9${"%.2f".format(expense.amountRupees)}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider()
            DetailRow("Date", PatientUtils.formatDateForDisplay(expense.expenseDate))
            DetailRow("Category", expense.category.label)
            if (!expense.description.isNullOrBlank()) DetailRow("Description", expense.description)
            DetailRow("Payment Method", expense.paymentMethod.label)
            if (!expense.referenceNumber.isNullOrBlank()) DetailRow("Reference Number", expense.referenceNumber)
            if (expense.attachmentPath != null) DetailRow("Attachment", "Receipt attached")
            HorizontalDivider()
            if (!expense.createdBy.isNullOrBlank()) DetailRow("Created By", expense.createdBy)
            if (!expense.createdAt.isNullOrBlank()) DetailRow("Created", PatientUtils.formatDateTimeForDisplay(expense.createdAt))
            if (!expense.updatedBy.isNullOrBlank()) DetailRow("Last Updated By", expense.updatedBy)
            if (!expense.updatedAt.isNullOrBlank()) DetailRow("Last Updated", PatientUtils.formatDateTimeForDisplay(expense.updatedAt))

            if (canManage) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
