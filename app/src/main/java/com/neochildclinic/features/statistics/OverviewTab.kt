package com.neochildclinic.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.core.designsystem.NeoChildTheme

@Composable
fun OverviewTab(patients: List<Patient>, vaccinations: List<Vaccination>, financeTransactions: List<FinanceEntity>) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val availableYears = remember(patients, vaccinations) {
        StatisticsUtils.getAvailableFinancialYears(patients.map { it.registrationDate ?: "" } + vaccinations.map { it.dateGiven } + financeTransactions.map { it.timestamp })
    }

    val filteredPatients = remember(patients, filterMode, fyQuarter, selectedMonth) {
        patients.filter { StatisticsUtils.isDateInFilter(it.registrationDate ?: "", filterMode, fyQuarter, selectedMonth) }
    }
    
    val filteredVaccinations = remember(vaccinations, filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.filterValidVaccinations(vaccinations).filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }

    val filteredTransactions = remember(financeTransactions, filterMode, fyQuarter, selectedMonth) {
        financeTransactions.filter { StatisticsUtils.isDateInFilter(it.timestamp, filterMode, fyQuarter, selectedMonth) }
    }

    val allValidVaccinations = remember(vaccinations) { StatisticsUtils.filterValidVaccinations(vaccinations) }
    val financeStats = remember(filteredTransactions, allValidVaccinations, filteredVaccinations) {
        FinanceCalculator.calculateFinanceStats(filteredTransactions, allValidVaccinations, financeTransactions, filteredVaccinations)
    }
    val stats = remember(filteredPatients, filteredVaccinations, financeStats) {
        listOf(
            StatItem("Registered in Period", filteredPatients.size.toString(), Icons.Default.PersonAdd, Color(0xFF4CAF50)),
            StatItem("Patients Vaccinated", filteredVaccinations.map { it.patientId }.distinct().size.toString(), Icons.Default.PersonSearch, Color(0xFF673AB7)),
            StatItem("Doses", filteredVaccinations.sumOf { v -> v.items.sumOf { it.quantity.coerceAtLeast(0) } }.toString(), Icons.Default.Vaccines, Color(0xFFFF9800)),
            StatItem("Revenue", "₹${financeStats.totalRevenue.toInt()}", Icons.Default.Payments, Color(0xFF3F51B5)),
            StatItem("Cash", "₹${financeStats.cashTotal.toInt()}", Icons.Default.Money, Color(0xFF4CAF50)),
            StatItem("Online", "₹${financeStats.onlineTotal.toInt()}", Icons.Default.AccountBalanceWallet, Color(0xFF03A9F4)),
            StatItem("Net Profit", if (financeStats.isProfitComplete) "₹${financeStats.netProfit.toInt()}" else "Unavailable", Icons.Default.TrendingUp, Color(0xFF009688))
        )
    }

    OverviewContent(
        stats = stats,
        filterMode = filterMode,
        fyQuarter = fyQuarter,
        selectedMonth = selectedMonth,
        availableYears = availableYears,
        onFilterModeChange = { 
            filterMode = it
            fyQuarter = 0
            selectedMonth = -1
        },
        onQuarterChange = { 
            fyQuarter = if (fyQuarter == it) 0 else it
            selectedMonth = -1
        },
        onMonthChange = { 
            selectedMonth = if (selectedMonth == it) -1 else it
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewContent(
    stats: List<StatItem>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    availableYears: List<String>,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    val mainOptions = remember(availableYears) { listOf("Overall") + availableYears.reversed().map { "FY $it" } }
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            OverviewHeader(
                filterMode = filterMode,
                fyQuarter = fyQuarter,
                selectedMonth = selectedMonth,
                mainOptions = mainOptions,
                onFilterModeChange = onFilterModeChange
            )
        }

        if (filterMode.startsWith("FY ")) {
            item {
                QuarterAndMonthFilters(
                    fyQuarter = fyQuarter,
                    selectedMonth = selectedMonth,
                    onQuarterChange = onQuarterChange,
                    onMonthChange = onMonthChange
                )
            }
        }

        item {
            StatsGrid(stats = stats)
        }
        
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun OverviewHeader(
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    mainOptions: List<String>,
    onFilterModeChange: (String) -> Unit
) {
    val subtitle = remember(filterMode, selectedMonth, fyQuarter) {
        when {
            filterMode == "Overall" -> "All Time"
            selectedMonth != -1 -> "${StatisticsUtils.monthNames[selectedMonth]} Statistics"
            fyQuarter != 0 -> "Quarter ${fyQuarter} Statistics"
            else -> "Annual Statistics ($filterMode)"
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        
        var expanded by remember { mutableStateOf(false) }
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(filterMode) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                mainOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onFilterModeChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuarterAndMonthFilters(
    fyQuarter: Int,
    selectedMonth: Int,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text("Quarters", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatisticsUtils.fyQuarters.forEachIndexed { index, _ ->
                val qNum = index + 1
                FilterChip(
                    selected = fyQuarter == qNum,
                    onClick = { onQuarterChange(qNum) },
                    label = { Text("Q$qNum") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        if (fyQuarter > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Months in Q$fyQuarter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatisticsUtils.fyQuarters[fyQuarter - 1].second.forEach { mIdx ->
                    FilterChip(
                        selected = selectedMonth == mIdx,
                        onClick = { onMonthChange(mIdx) },
                        label = { Text(StatisticsUtils.monthNames[mIdx], style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: List<StatItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in stats.indices step 2) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCardSmall(Modifier.weight(1f), stats[i].title, stats[i].value, stats[i].icon, stats[i].color)
                Spacer(modifier = Modifier.width(12.dp))
                if (i + 1 < stats.size) {
                    StatCardSmall(Modifier.weight(1f), stats[i + 1].title, stats[i + 1].value, stats[i + 1].icon, stats[i + 1].color)
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OverviewTabPreview() {
    NeoChildTheme {
        OverviewContent(
            stats = listOf(StatItem("New Patients", "10", Icons.Default.PersonAdd, Color.Green)),
            filterMode = "Overall",
            fyQuarter = 0,
            selectedMonth = -1,
            availableYears = listOf("2023-24"),
            onFilterModeChange = {},
            onQuarterChange = {},
            onMonthChange = {}
        )
    }
}
