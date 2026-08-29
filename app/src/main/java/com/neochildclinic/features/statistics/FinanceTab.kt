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
import com.neochildclinic.core.designsystem.*
import java.util.*

@Composable
fun FinanceTab(
    vaccinations: List<Vaccination>,
    transactions: List<FinanceEntity>,
    onMonthClick: (String) -> Unit = {}
) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val validVaccinations = remember(vaccinations) {
        StatisticsUtils.filterValidVaccinations(vaccinations)
    }
    val availableYears = remember(transactions, validVaccinations) {
        StatisticsUtils.getAvailableFinancialYears(
            transactions.map { it.timestamp } + validVaccinations.map { it.dateGiven }
        )
    }
    
    // Current period
    val filteredTransactions = remember(transactions, filterMode, fyQuarter, selectedMonth) {
        transactions.filter { StatisticsUtils.isDateInFilter(it.timestamp, filterMode, fyQuarter, selectedMonth) }
    }
    val filteredVaccinations = remember(validVaccinations, filterMode, fyQuarter, selectedMonth) {
        validVaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }
    
    // Previous period
    val (prevFilter, prevQuarter, prevMonth) = remember(filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.getPreviousPeriodFilter(filterMode, fyQuarter, selectedMonth)
    }
    val prevTransactions = remember(transactions, prevFilter, prevQuarter, prevMonth) {
        transactions.filter { StatisticsUtils.isDateInFilter(it.timestamp, prevFilter, prevQuarter, prevMonth) }
    }
    val prevVaccinations = remember(validVaccinations, prevFilter, prevQuarter, prevMonth) {
        validVaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, prevFilter, prevQuarter, prevMonth) }
    }

    val currentStats = remember(filteredTransactions, validVaccinations) {
        FinanceCalculator.calculateFinanceStats(filteredTransactions, validVaccinations, transactions, filteredVaccinations)
    }
    val prevStats = remember(prevTransactions, validVaccinations) {
        FinanceCalculator.calculateFinanceStats(prevTransactions, validVaccinations, transactions, prevVaccinations)
    }

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
        vaccinations = validVaccinations
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
    vaccinations: List<Vaccination>
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
        Text("Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        SummaryCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Total Revenue",
            value = String.format(Locale.US, "₹%,.0f", currentStats.totalRevenue),
            icon = Icons.Default.CurrencyRupee,
            iconColor = customColors.textBlue,
            iconBackground = customColors.softBlue,
            growthPercentage = StatisticsUtils.calculateGrowth(currentStats.totalRevenue, prevStats.totalRevenue)
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
                growthPercentage = StatisticsUtils.calculateGrowth(currentStats.cashTotal, prevStats.cashTotal)
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Online",
                value = String.format(Locale.US, "₹%,.0f", currentStats.onlineTotal),
                icon = Icons.Default.CreditCard,
                iconColor = customColors.textBlue,
                iconBackground = customColors.softBlue,
                growthPercentage = StatisticsUtils.calculateGrowth(currentStats.onlineTotal, prevStats.onlineTotal)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Expenses",
                value = String.format(Locale.US, "₹%,.0f", currentStats.totalExpenses),
                icon = Icons.Default.RemoveCircle,
                iconColor = customColors.textPink,
                iconBackground = customColors.softPink,
                growthPercentage = StatisticsUtils.calculateGrowth(currentStats.totalExpenses, prevStats.totalExpenses)
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Net Profit",
                value = if (currentStats.isProfitComplete) String.format(Locale.US, "₹%,.0f", currentStats.netProfit) else "Unavailable",
                icon = Icons.Default.TrendingUp,
                iconColor = customColors.textCyan,
                iconBackground = customColors.softCyan,
                growthPercentage = if (currentStats.isProfitComplete && prevStats.isProfitComplete) StatisticsUtils.calculateGrowth(currentStats.netProfit, prevStats.netProfit) else null
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
        Text("Financial Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        FinanceTable(
            transactions = filteredTransactions,
            vaccinations = vaccinations,
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onMonthClick = onMonthClick
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
        val currentFY = if (filterMode == "Overall") "Overall" else filterMode.substringAfter("FY ").let { "20$it" }
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
