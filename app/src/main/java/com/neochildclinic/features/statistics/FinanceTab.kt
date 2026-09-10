package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.core.designsystem.*
import java.util.*

@Composable
fun FinanceTab(
    vaccinations: List<Vaccination>,
    transactions: List<FinanceEntity>,
    expenses: List<Expense> = emptyList(),
    onMonthClick: (String) -> Unit = {}
) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val validVaccinations = remember(vaccinations) {
        StatisticsUtils.filterValidVaccinations(vaccinations)
    }
    // Raw (status-unfiltered) visit dates - Financial Statistics must resolve a linked
    // transaction's reporting date from the actual visit (vaccination or consultation)
    // regardless of that visit's clinical/administered status, which is an orthogonal
    // concept. See FinanceCalculator.resolveReportingDate.
    val visitDatesById = remember(vaccinations) { vaccinations.associate { it.id to it.dateGiven } }

    // Optimization: Resolve reporting dates once when transactions or visit dates change.
    // This avoids repeated expensive date parsing inside filter loops, which was causing the UI to hang.
    val transactionsWithDates = remember(transactions, visitDatesById) {
        transactions.map { it to FinanceCalculator.resolveReportingDate(it, visitDatesById) }
    }

    val availableYears = remember(transactionsWithDates, vaccinations) {
        StatisticsUtils.getAvailableFinancialYears(
            transactionsWithDates.map { it.second } + vaccinations.map { it.dateGiven }
        )
    }
    
    // Current period
    val filteredTransactions = remember(transactionsWithDates, filterMode, fyQuarter, selectedMonth) {
        transactionsWithDates.filter {
            StatisticsUtils.isDateInFilter(it.second, filterMode, fyQuarter, selectedMonth)
        }.map { it.first }
    }
    val filteredVaccinations = remember(validVaccinations, filterMode, fyQuarter, selectedMonth) {
        validVaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }
    
    // Previous period
    val (prevFilter, prevQuarter, prevMonth) = remember(filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.getPreviousPeriodFilter(filterMode, fyQuarter, selectedMonth)
    }
    val prevTransactions = remember(transactionsWithDates, prevFilter, prevQuarter, prevMonth) {
        transactionsWithDates.filter {
            StatisticsUtils.isDateInFilter(it.second, prevFilter, prevQuarter, prevMonth)
        }.map { it.first }
    }
    val prevVaccinations = remember(validVaccinations, prevFilter, prevQuarter, prevMonth) {
        validVaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, prevFilter, prevQuarter, prevMonth) }
    }

    val currentStats = remember(filteredTransactions, validVaccinations, visitDatesById) {
        FinanceCalculator.calculateFinanceStats(filteredTransactions, validVaccinations, transactions, filteredVaccinations, visitDatesById)
    }
    val prevStats = remember(prevTransactions, validVaccinations, visitDatesById) {
        FinanceCalculator.calculateFinanceStats(prevTransactions, validVaccinations, transactions, prevVaccinations, visitDatesById)
    }

    // Additive expense-table totals (task section 7) - computed with the exact same FY
    // window as everything else on this tab, kept fully separate from currentStats/
    // prevStats (FinanceStatsData.totalExpenses/netProfit, from finance_transactions,
    // are untouched).
    val currentExpensesPaise = remember(expenses, filterMode, fyQuarter, selectedMonth) {
        ExpenseCalculator.totalExpensesPaiseInPeriod(expenses, filterMode, fyQuarter, selectedMonth)
    }
    val prevExpensesPaise = remember(expenses, prevFilter, prevQuarter, prevMonth) {
        ExpenseCalculator.totalExpensesPaiseInPeriod(expenses, prevFilter, prevQuarter, prevMonth)
    }
    val netIncome = ExpenseCalculator.netIncomeRupees(currentStats.totalRevenue, currentExpensesPaise)
    val prevNetIncome = ExpenseCalculator.netIncomeRupees(prevStats.totalRevenue, prevExpensesPaise)

    FinanceContent(
        currentStats = currentStats,
        prevStats = prevStats,
        filterMode = filterMode,
        availableYears = availableYears,
        transactions = transactions,
        filteredTransactions = filteredTransactions,
        fyQuarter = fyQuarter,
        selectedMonth = selectedMonth,
        onFilterModeChange = { filterMode = "FY ${it.takeLast(5)}"; fyQuarter = 0; selectedMonth = -1 },
        onQuarterChange = { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 },
        onMonthChange = { selectedMonth = if (selectedMonth == it) -1 else it },
        onMonthClick = onMonthClick,
        vaccinations = vaccinations,
        currentExpensesRupees = currentExpensesPaise / 100.0,
        prevExpensesRupees = prevExpensesPaise / 100.0,
        netIncome = netIncome,
        prevNetIncome = prevNetIncome,
        visitDatesById = visitDatesById
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceContent(
    currentStats: FinanceStatsData,
    prevStats: FinanceStatsData,
    filterMode: String,
    availableYears: List<String>,
    transactions: List<FinanceEntity>,
    filteredTransactions: List<FinanceEntity>,
    fyQuarter: Int,
    selectedMonth: Int,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onMonthClick: (String) -> Unit,
    vaccinations: List<Vaccination>,
    currentExpensesRupees: Double = 0.0,
    prevExpensesRupees: Double = 0.0,
    netIncome: Double = 0.0,
    prevNetIncome: Double = 0.0,
    visitDatesById: Map<String, String>? = null
) {
    val customColors = LocalCustomColors.current

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        FilterSection(
            availableYears = availableYears.reversed().map { "20$it" },
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onFilterModeChange = onFilterModeChange,
            onQuarterChange = onQuarterChange,
            onMonthChange = onMonthChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Surfaces FinanceStatsData.invalidTransactionDateCount, which was already being
        // computed but never shown anywhere - a transaction whose date/timestamp genuinely
        // can't be parsed silently disappears from every total (including Overall) with no
        // indication to the user. This makes that visible instead of leaving totals looking
        // mysteriously low with no way to tell why.
        if (currentStats.invalidTransactionDateCount > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${currentStats.invalidTransactionDateCount} transaction(s) have a date that couldn't be read and are excluded from every total shown here.",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        SummaryCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Total Revenue",
            value = String.format(Locale.US, "₹%,.0f", currentStats.totalRevenue),
            icon = Icons.Default.CurrencyRupee,
            iconColor = customColors.textBlue,
            iconBackground = customColors.softBlue,
            growthPercentage = if (filterMode == "Overall") null else StatisticsUtils.calculateGrowth(currentStats.totalRevenue, prevStats.totalRevenue)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Cash",
                value = String.format(Locale.US, "₹%,.0f", currentStats.cashTotal),
                icon = Icons.Default.Payments,
                iconColor = customColors.textGreen,
                iconBackground = customColors.softGreen,
                growthPercentage = if (filterMode == "Overall") null else StatisticsUtils.calculateGrowth(currentStats.cashTotal, prevStats.cashTotal)
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Online",
                value = String.format(Locale.US, "₹%,.0f", currentStats.onlineTotal),
                icon = Icons.Default.CreditCard,
                iconColor = customColors.textBlue,
                iconBackground = customColors.softBlue,
                growthPercentage = if (filterMode == "Overall") null else StatisticsUtils.calculateGrowth(currentStats.onlineTotal, prevStats.onlineTotal)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "COGS + Expense",
                value = String.format(Locale.US, "₹%,.0f", currentStats.vaccineCost + currentStats.totalExpenses),
                icon = Icons.Default.RemoveCircle,
                iconColor = customColors.textPink,
                iconBackground = customColors.softPink,
                growthPercentage = if (filterMode == "Overall") null else StatisticsUtils.calculateGrowth(currentStats.vaccineCost + currentStats.totalExpenses, prevStats.vaccineCost + prevStats.totalExpenses)
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Net Profit",
                value = if (currentStats.isProfitComplete) String.format(Locale.US, "₹%,.0f", currentStats.netProfit) else "Unavailable",
                icon = Icons.Default.TrendingUp,
                iconColor = customColors.textCyan,
                iconBackground = customColors.softCyan,
                growthPercentage = if (filterMode == "Overall" || !currentStats.isProfitComplete || !prevStats.isProfitComplete) null else StatisticsUtils.calculateGrowth(currentStats.netProfit, prevStats.netProfit)
            )
        }

        if (!currentStats.isProfitComplete) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Incomplete Profit Calculation", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Some vaccination records are missing historical cost data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Expenses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        // Additive section (task section 7): sourced from the separate `expenses` table,
        // not merged into currentStats/prevStats above (which remain finance_transactions-
        // only, unchanged). Total Income here is currentStats.totalRevenue (vaccination +
        // consultation income, already computed by FinanceCalculator).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Total Expenses",
                value = String.format(Locale.US, "₹%,.0f", currentExpensesRupees),
                icon = Icons.Default.Receipt,
                iconColor = customColors.textPink,
                iconBackground = customColors.softPink,
                growthPercentage = if (filterMode == "Overall") null else StatisticsUtils.calculateGrowth(currentExpensesRupees, prevExpensesRupees)
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Net Income",
                value = String.format(Locale.US, "₹%,.0f", netIncome),
                icon = Icons.Default.TrendingUp,
                iconColor = customColors.textCyan,
                iconBackground = customColors.softCyan,
                growthPercentage = if (filterMode == "Overall") null else StatisticsUtils.calculateGrowth(netIncome, prevNetIncome)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Financial Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        FinanceTable(
            transactions = filteredTransactions,
            vaccinations = vaccinations,
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onMonthClick = onMonthClick,
            visitDatesById = visitDatesById
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    availableYears: List<String>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Financial Year Dropdown
        var yearExpanded by remember { mutableStateOf(false) }
        val currentFY = StatisticsUtils.displayFilterMode(filterMode)
        ExposedDropdownMenuBox(
            expanded = yearExpanded,
            onExpandedChange = { yearExpanded = it },
            modifier = Modifier.weight(1.3f)
        ) {
            OutlinedTextField(
                value = "Financial Year  $currentFY",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                availableYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year) },
                        onClick = { onFilterModeChange(year); yearExpanded = false }
                    )
                }
            }
        }

        // Quarter Dropdown
        var qExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = qExpanded,
            onExpandedChange = { qExpanded = it },
            modifier = Modifier.weight(0.9f)
        ) {
            OutlinedTextField(
                value = if (fyQuarter == 0) "Quarter  All" else "Quarter  Q$fyQuarter",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            ExposedDropdownMenu(expanded = qExpanded, onDismissRequest = { qExpanded = false }) {
                DropdownMenuItem(text = { Text("All") }, onClick = { onQuarterChange(0); qExpanded = false })
                (1..4).forEach { q ->
                    DropdownMenuItem(text = { Text("Q$q") }, onClick = { onQuarterChange(q); qExpanded = false })
                }
            }
        }

        // Month Dropdown
        var mExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = mExpanded,
            onExpandedChange = { mExpanded = it },
            modifier = Modifier.weight(0.8f)
        ) {
            OutlinedTextField(
                value = if (selectedMonth == -1) "Month  All" else "Month  ${StatisticsUtils.monthNames[selectedMonth]}",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            ExposedDropdownMenu(expanded = mExpanded, onDismissRequest = { mExpanded = false }) {
                DropdownMenuItem(text = { Text("All") }, onClick = { onMonthChange(-1); mExpanded = false })
                val months = if (fyQuarter == 0) (0..11).toList() else StatisticsUtils.fyQuarters[fyQuarter - 1].second
                months.forEach { mIdx ->
                    DropdownMenuItem(text = { Text(StatisticsUtils.monthNames[mIdx]) }, onClick = { onMonthChange(mIdx); mExpanded = false })
                }
            }
        }
    }
}

@Composable
private fun FinanceQuarterAndMonthFilters(
    fyQuarter: Int,
    selectedMonth: Int,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatisticsUtils.fyQuarters.forEachIndexed { index, _ ->
                val q = index + 1
                FilterChip(selected = fyQuarter == q, onClick = { onQuarterChange(q) }, label = { Text("Q$q") }, modifier = Modifier.weight(1f))
            }
        }
        if (fyQuarter > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatisticsUtils.fyQuarters[fyQuarter - 1].second.forEach { month ->
                    FilterChip(selected = selectedMonth == month, onClick = { onMonthChange(month) }, label = { Text(StatisticsUtils.monthNames[month]) })
                }
            }
        }
    }
}

@Composable
private fun FinanceMetricRow(label1: String, amount1: Double, label2: String, amount2: Double, valuesAvailable: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FinanceMetricCard(label1, amount1, if (label2.isBlank()) Modifier.fillMaxWidth() else Modifier.weight(1f), valuesAvailable)
        if (label2.isNotBlank()) FinanceMetricCard(label2, amount2, Modifier.weight(1f), valuesAvailable)
    }
}

@Composable
private fun FinanceMetricCard(label: String, amount: Double, modifier: Modifier, valuesAvailable: Boolean = true) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                if (valuesAvailable) "₹${amount.toInt()}" else "Unavailable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FinanceTabPreview() {
    NeoChildTheme {
        FinanceContent(
            currentStats = FinanceStatsData(totalRevenue = 10000.0, cashTotal = 6000.0, onlineTotal = 4000.0, totalExpenses = 3000.0, vaccineCost = 500.0, grossProfit = 9500.0, netProfit = 6500.0),
            prevStats = FinanceStatsData(totalRevenue = 8000.0, cashTotal = 5000.0, onlineTotal = 3000.0, totalExpenses = 2000.0, vaccineCost = 400.0, grossProfit = 7600.0, netProfit = 5600.0),
            filterMode = "Overall",
            availableYears = listOf("23-24"),
            transactions = emptyList(),
            filteredTransactions = emptyList(),
            fyQuarter = 0,
            selectedMonth = -1,
            onFilterModeChange = {},
            onQuarterChange = {},
            onMonthChange = {},
            onMonthClick = {},
            vaccinations = emptyList()
        )
    }
}
