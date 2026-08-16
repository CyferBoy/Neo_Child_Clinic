package com.neochildclinic.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.core.designsystem.NeoChildTheme

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
    val filteredTransactions = remember(transactions, filterMode, fyQuarter, selectedMonth) {
        transactions.filter { StatisticsUtils.isDateInFilter(it.timestamp, filterMode, fyQuarter, selectedMonth) }
    }
    val filteredVaccinations = remember(validVaccinations, filterMode, fyQuarter, selectedMonth) {
        validVaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }
    // Financial period is defined by transaction dates. Keep the full valid vaccination
    // set available so vaccine COGS can always be resolved from the linked visit.
    val financeStats = remember(filteredTransactions, validVaccinations) {
        FinanceCalculator.calculateFinanceStats(filteredTransactions, validVaccinations, transactions, filteredVaccinations)
    }

    FinanceContent(
        stats = financeStats,
        filterMode = filterMode,
        availableYears = availableYears,
        vaccinations = validVaccinations,
        filteredVaccinations = filteredVaccinations,
        transactions = transactions,
        filteredTransactions = filteredTransactions,
        fyQuarter = fyQuarter,
        selectedMonth = selectedMonth,
        onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
        onQuarterChange = { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 },
        onMonthChange = { selectedMonth = if (selectedMonth == it) -1 else it },
        onMonthClick = onMonthClick
    )
}

@Composable
private fun FinanceContent(
    stats: FinanceStatsData,
    filterMode: String,
    availableYears: List<String>,
    vaccinations: List<Vaccination>,
    filteredVaccinations: List<Vaccination>,
    transactions: List<FinanceEntity>,
    filteredTransactions: List<FinanceEntity>,
    fyQuarter: Int,
    selectedMonth: Int,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onMonthClick: (String) -> Unit
) {
    val mainOptions = remember(availableYears) { listOf("Overall") + availableYears.reversed().map { "FY $it" } }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        FinanceHeader(filterMode = filterMode, mainOptions = mainOptions, onFilterModeChange = onFilterModeChange)
        if (filterMode.startsWith("FY ")) {
            FinanceQuarterAndMonthFilters(fyQuarter, selectedMonth, onQuarterChange, onMonthChange)
        }

        RevenueOverviewCard(revenue = stats.totalRevenue, filterMode = filterMode)
        Spacer(modifier = Modifier.height(16.dp))
        FinanceMetricRow(
            label1 = "Expenses", amount1 = stats.totalExpenses,
            label2 = "", amount2 = 0.0
        )
        Spacer(modifier = Modifier.height(12.dp))
        FinanceMetricRow(
            label1 = "Vaccine Cost", amount1 = stats.vaccineCost,
            label2 = "", amount2 = 0.0
        )
        Spacer(modifier = Modifier.height(12.dp))
        FinanceMetricRow(
            label1 = "Gross Profit", amount1 = stats.grossProfit,
            label2 = "Net Profit", amount2 = stats.netProfit,
            valuesAvailable = stats.isProfitComplete
        )

        if (stats.invalidTimestampCount > 0 || stats.unmatchedVaccinationIncomeCount > 0 || stats.missingCogsSnapshotCount > 0 || stats.unrecordedVaccinationPaymentCount > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Financial data needs attention", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    if (stats.invalidTimestampCount > 0) {
                        Text("${stats.invalidTimestampCount} transaction(s) have invalid dates and are excluded from monthly reports.", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    if (stats.unmatchedVaccinationIncomeCount > 0) {
                        Text("${stats.unmatchedVaccinationIncomeCount} vaccination income transaction(s) have no valid vaccination record; vaccine cost could not be resolved.", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    if (stats.missingCogsSnapshotCount > 0) {
                        Text("${stats.missingCogsSnapshotCount} vaccination income transaction(s) are missing a historical COGS snapshot and are excluded from COGS/profit until migrated.", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    if (stats.unrecordedVaccinationPaymentCount > 0) {
                        Text("${stats.unrecordedVaccinationPaymentCount} paid vaccination record(s) have no matching finance income transaction.", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        CashOnlineSummary(cash = stats.cashTotal, online = stats.onlineTotal)

        Spacer(modifier = Modifier.height(24.dp))
        Text("Financial Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        FinanceTable(
            transactions = filteredTransactions,
            vaccinations = vaccinations,
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onMonthClick = onMonthClick
        )

        Spacer(modifier = Modifier.height(32.dp))
        if (fyQuarter == 0 && selectedMonth == -1) {
            ChartsSection(filterMode = filterMode, availableYears = availableYears, transactions = filteredTransactions, vaccinations = vaccinations)
            Spacer(modifier = Modifier.height(16.dp))
            ChartLegend()
        } else {
            Text(
                "Trend charts are shown for the selected financial year. Clear the quarter/month filter to view the full trend.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
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
            stats = FinanceStatsData(totalRevenue = 10000.0, cashTotal = 6000.0, onlineTotal = 4000.0, totalExpenses = 3000.0, vaccineCost = 500.0, grossProfit = 9500.0, netProfit = 6500.0),
            filterMode = "Overall",
            availableYears = listOf("23-24"),
            vaccinations = emptyList(),
            filteredVaccinations = emptyList(),
            transactions = emptyList(),
            filteredTransactions = emptyList(),
            fyQuarter = 0,
            selectedMonth = -1,
            onFilterModeChange = {},
            onQuarterChange = {},
            onMonthChange = {},
            onMonthClick = {}
        )
    }
}
