package com.neochildclinic.feature.finance.presentation

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.ErrorState
import com.neochildclinic.core.ui.EmptyState
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.DeleteConfirmationDialog
import com.neochildclinic.core.ui.SearchTopAppBar
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.ui.SkeletonListItem
import com.neochildclinic.core.common.PatientUtils
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
    var isSearchActive by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val loadError = uiState.error

    ShowSnackbar(loadError, snackbarHostState) { viewModel.clearError() }
    ShowSnackbar(uiState.deletedMessage, snackbarHostState, duration = SnackbarDuration.Short) { viewModel.clearDeletedMessage() }

    val filtersActive = uiState.categoryFilter != null || uiState.paymentMethodFilter != null ||
        uiState.fromDate.isNotBlank() || uiState.toDate.isNotBlank()

    val totalAmount = remember(uiState.expenses) {
        uiState.expenses.sumOf { it.amountRupees }
    }
    val totalCount = remember(uiState.expenses) {
        uiState.expenses.size
    }
    val biggestCategoryGroup = remember(uiState.expenses) {
        uiState.expenses.groupBy { it.category }
            .maxByOrNull { entry -> entry.value.sumOf { it.amountRupees } }
    }
    val biggestCategoryName = biggestCategoryGroup?.key?.label ?: "None"
    val biggestCategoryTotal = biggestCategoryGroup?.value?.sumOf { it.amountRupees } ?: 0.0
    val biggestCategoryPercent = if (totalAmount > 0) ((biggestCategoryTotal / totalAmount) * 100).toInt() else 0

    val monthName = remember {
        java.time.LocalDate.now().month.name.uppercase()
    }
    val currentMonthDisplay = remember {
        val now = java.time.LocalDate.now()
        "${now.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${now.year}"
    }

    val groupedExpenses = remember(uiState.expenses) { groupExpensesByMonth(uiState.expenses) }
    val collapsedMonths = remember { mutableStateMapOf<String, Boolean>() }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.card)) {
                            Text(
                                text = "$monthName EXPENSES",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = "₹${String.format(java.util.Locale.ENGLISH, "%,.0f", totalAmount)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB42318)
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = "$totalCount records",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.card)) {
                            Text(
                                text = "BIGGEST",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = biggestCategoryName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = "₹${String.format(java.util.Locale.ENGLISH, "%,.0f", biggestCategoryTotal)} · $biggestCategoryPercent%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val popularCategories = listOf(
                    null,
                    ExpenseCategory.STAFF_SALARY,
                    ExpenseCategory.RENT,
                    ExpenseCategory.MEDICAL_SUPPLIES,
                    ExpenseCategory.OTHER
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    popularCategories.forEach { category ->
                        val isSelected = uiState.categoryFilter == category
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            onClick = { viewModel.onCategoryFilterChange(category) },
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = category?.label ?: "All",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.fromDate.isNotBlank()) "${uiState.fromDate} – ${uiState.toDate}" else currentMonthDisplay,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiState.sort.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AppPullToRefresh(isRefreshing = uiState.isRefreshing, onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                    if (uiState.isLoading && uiState.expenses.isEmpty()) {
                        SkeletonList(
                            modifier = Modifier.fillMaxSize(),
                            count = 8,
                            cardShaped = true,
                            spacing = Spacing.sm,
                            contentPadding = PaddingValues(Spacing.lg)
                        )
                    } else if (!uiState.isLoading && uiState.expenses.isEmpty() && loadError != null) {
                        ErrorState(
                            modifier = Modifier.fillMaxSize(),
                            message = loadError,
                            onRetry = {
                                viewModel.clearError()
                                viewModel.refresh()
                            }
                        )
                    } else if (!uiState.isLoading && uiState.expenses.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.fillMaxSize(),
                            title = if (filtersActive || uiState.query.isNotBlank()) "No expenses match these filters" else "No expenses recorded yet"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            groupedExpenses.forEach { group ->
                                val isCollapsed = collapsedMonths[group.monthKey] ?: false
                                item(key = "header_${group.monthKey}") {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 4.dp)
                                            .clickable {
                                                collapsedMonths[group.monthKey] = !isCollapsed
                                            },
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = group.monthDisplay,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${group.expenses.size} expenses · ₹${String.format(java.util.Locale.ENGLISH, "%,.0f", group.totalAmount)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (!isCollapsed) {
                                    items(group.expenses, key = { it.id }) { expense ->
                                        ExpenseCard(expense = expense, onClick = { selectedExpense = expense })
                                    }
                                }
                            }
                            if (uiState.canLoadMore) {
                                item {
                                    if (uiState.isLoadingMore) {
                                        SkeletonListItem(showLeadingIcon = true, showTrailing = false)
                                    } else {
                                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md), contentAlignment = Alignment.Center) {
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
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

        var fyExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = fyExpanded, onExpandedChange = { fyExpanded = !fyExpanded }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = uiState.selectedFinancialYear,
                onValueChange = {},
                readOnly = true,
                label = { Text("Financial Year") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fyExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = fyExpanded, onDismissRequest = { fyExpanded = false }) {
                uiState.availableFinancialYears.forEach { fy ->
                    DropdownMenuItem(
                        text = { Text(fy) },
                        onClick = {
                            viewModel.onFinancialYearChange(fy)
                            fyExpanded = false
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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

private fun categoryColor(category: ExpenseCategory): Pair<Color, Color> {
    return when (category) {
        ExpenseCategory.STAFF_SALARY -> Pair(Color(0xFFDCE9F8), Color(0xFF0A2F52))
        ExpenseCategory.RENT -> Pair(Color(0xFFE0EBE2), Color(0xFF244032))
        ExpenseCategory.MEDICAL_SUPPLIES, ExpenseCategory.VACCINE_PURCHASE -> Pair(Color(0xFFF8E4D7), Color(0xFF5B2A12))
        ExpenseCategory.ELECTRICITY, ExpenseCategory.WATER, ExpenseCategory.INTERNET -> Pair(Color(0xFFF0EAFB), Color(0xFF3F2482))
        else -> Pair(Color(0xFFEEF0F2), Color(0xFF344054))
    }
}

private fun categoryIcon(category: ExpenseCategory): ImageVector {
    return when (category) {
        ExpenseCategory.STAFF_SALARY -> Icons.Default.People
        ExpenseCategory.RENT -> Icons.Default.Home
        ExpenseCategory.MEDICAL_SUPPLIES, ExpenseCategory.VACCINE_PURCHASE -> Icons.Default.MedicalServices
        else -> Icons.Default.ReceiptLong
    }
}

@Composable
private fun ExpenseCard(expense: Expense, onClick: () -> Unit) {
    val (bgTint, iconTint) = categoryColor(expense.category)
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.card).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = bgTint
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = categoryIcon(expense.category),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${expense.category.label} · ${PatientUtils.formatDateForDisplay(expense.expenseDate)} · ${expense.paymentMethod.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = com.neochildclinic.core.common.CurrencyUtils.formatInr(expense.amountRupees),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (expense.attachmentPath != null) {
                    Text(
                        text = "receipt",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF067647),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF7C8A84),
                modifier = Modifier.size(16.dp)
            )
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(expense.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                com.neochildclinic.core.common.CurrencyUtils.formatInr(expense.amountRupees),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            DetailRow("Date", PatientUtils.formatDateForDisplay(expense.expenseDate))
            DetailRow("Category", expense.category.label)
            if (!expense.description.isNullOrBlank()) DetailRow("Description", expense.description)
            DetailRow("Payment Method", expense.paymentMethod.label)
            if (!expense.referenceNumber.isNullOrBlank()) DetailRow("Reference Number", expense.referenceNumber)
            if (expense.attachmentPath != null) DetailRow("Attachment", "Receipt attached")
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            if (!expense.createdBy.isNullOrBlank()) DetailRow("Created By", expense.createdBy)
            if (!expense.createdAt.isNullOrBlank()) DetailRow("Created", PatientUtils.formatDateTimeForDisplay(expense.createdAt))
            if (!expense.updatedBy.isNullOrBlank()) DetailRow("Last Updated By", expense.updatedBy)
            if (!expense.updatedAt.isNullOrBlank()) DetailRow("Last Updated", PatientUtils.formatDateTimeForDisplay(expense.updatedAt))

            if (canManage) {
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
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

private data class MonthExpenseGroup(
    val monthKey: String,
    val monthDisplay: String,
    val totalAmount: Double,
    val expenses: List<Expense>
)

private fun groupExpensesByMonth(expenses: List<Expense>): List<MonthExpenseGroup> {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.US)
    val map = linkedMapOf<String, MutableList<Expense>>()

    for (expense in expenses) {
        val (key, display) = runCatching {
            val date = java.time.LocalDate.parse(expense.expenseDate.take(10))
            val key = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM", java.util.Locale.US))
            val display = date.format(formatter)
            key to display
        }.getOrElse {
            "earlier" to "Earlier"
        }
        map.getOrPut("$key::$display") { mutableListOf() }.add(expense)
    }

    return map.map { (keyDisplay, list) ->
        val parts = keyDisplay.split("::")
        val key = parts[0]
        val display = parts.getOrElse(1) { key }
        MonthExpenseGroup(
            monthKey = key,
            monthDisplay = display,
            totalAmount = list.sumOf { it.amountRupees },
            expenses = list
        )
    }
}
