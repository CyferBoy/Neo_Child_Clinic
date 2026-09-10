package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.utils.PatientUtils
import java.util.*

@Composable
fun OverviewTab(
    patients: List<Patient>,
    vaccinations: List<Vaccination>,
    financeTransactions: List<FinanceEntity>
) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val availableYears = remember(patients, vaccinations, financeTransactions) {
        StatisticsUtils.getAvailableFinancialYears(
            patients.map { it.registrationDate ?: "" } +
                    vaccinations.map { it.dateGiven } +
                    financeTransactions.map { FinanceCalculator.resolveReportingDate(it) }
        )
    }

    // Current period data
    val filteredPatients = remember(patients, filterMode, fyQuarter, selectedMonth) {
        patients.filter { StatisticsUtils.isDateInFilter(it.registrationDate ?: "", filterMode, fyQuarter, selectedMonth) }
    }
    val filteredVaccinations = remember(vaccinations, filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.filterValidVaccinations(vaccinations).filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }
    val filteredTransactions = remember(financeTransactions, filterMode, fyQuarter, selectedMonth) {
        financeTransactions.filter { StatisticsUtils.isDateInFilter(FinanceCalculator.resolveReportingDate(it), filterMode, fyQuarter, selectedMonth) }
    }

    // Previous period data for growth calculation
    val (prevFilter, prevQuarter, prevMonth) = remember(filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.getPreviousPeriodFilter(filterMode, fyQuarter, selectedMonth)
    }
    val prevPatients = remember(patients, prevFilter, prevQuarter, prevMonth) {
        patients.filter { StatisticsUtils.isDateInFilter(it.registrationDate ?: "", prevFilter, prevQuarter, prevMonth) }
    }
    val prevVaccinations = remember(vaccinations, prevFilter, prevQuarter, prevMonth) {
        StatisticsUtils.filterValidVaccinations(vaccinations).filter { StatisticsUtils.isDateInFilter(it.dateGiven, prevFilter, prevQuarter, prevMonth) }
    }
    val prevTransactions = remember(financeTransactions, prevFilter, prevQuarter, prevMonth) {
        financeTransactions.filter { StatisticsUtils.isDateInFilter(FinanceCalculator.resolveReportingDate(it), prevFilter, prevQuarter, prevMonth) }
    }

    val allValidVaccinations = remember(vaccinations) { StatisticsUtils.filterValidVaccinations(vaccinations) }
    
    val currentFinanceStats = remember(filteredTransactions, allValidVaccinations, filteredVaccinations) {
        FinanceCalculator.calculateFinanceStats(filteredTransactions, allValidVaccinations, financeTransactions, filteredVaccinations)
    }
    val prevFinanceStats = remember(prevTransactions, allValidVaccinations, prevVaccinations) {
        FinanceCalculator.calculateFinanceStats(prevTransactions, allValidVaccinations, financeTransactions, prevVaccinations)
    }

    // Quick Overview Chart Data (Last 6 Months)
    // Previously this re-filtered the FULL patients/vaccinations/transactions lists once
    // per month (6x), re-parsing every date string each time and even re-running
    // filterValidVaccinations() (itself a full-list pass) inside the loop - O(6*N) scans
    // and O(6*N) date parses for a chart that only needs 6 numbers. Rewritten below to do
    // a single pass over each list, bucketing by year/month, then just look up the 6
    // months needed. allValidVaccinations is already computed above, so it's reused
    // instead of being recomputed here.
    val trendData = remember(patients, vaccinations, financeTransactions, allValidVaccinations) {
        val cal = Calendar.getInstance()
        val months = (0 until 6).reversed().map { monthOffset ->
            val tempCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -monthOffset) }
            val key = tempCal.get(Calendar.YEAR) * 12 + tempCal.get(Calendar.MONTH)
            key to StatisticsUtils.monthNames[tempCal.get(Calendar.MONTH)]
        }
        val monthKeys = months.map { it.first }.toSet()

        fun yearMonthKey(dateStr: String): Int? {
            val d = PatientUtils.parseDate(dateStr) ?: return null
            val c = Calendar.getInstance().apply { time = d }
            return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        }

        val patientCounts = patients.asSequence()
            .mapNotNull { yearMonthKey(it.registrationDate ?: "") }
            .filter { it in monthKeys }
            .groupingBy { it }.eachCount()

        val vaccinationCounts = allValidVaccinations.asSequence()
            .mapNotNull { yearMonthKey(it.dateGiven) }
            .filter { it in monthKeys }
            .groupingBy { it }.eachCount()

        val revenueByMonth = financeTransactions.asSequence()
            .filter { it.type.equals("INCOME", true) }
            .mapNotNull { t -> yearMonthKey(FinanceCalculator.resolveReportingDate(t))?.let { it to t.amount } }
            .filter { it.first in monthKeys }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.sum() }

        months.map { (key, monthLabel) ->
            val mPatients = (patientCounts[key] ?: 0).toFloat()
            val mVaccinations = (vaccinationCounts[key] ?: 0).toFloat()
            val mRevenue = ((revenueByMonth[key] ?: 0.0) / 1000.0).toFloat() // K-scale for revenue
            ChartDataPoint(monthLabel, listOf(mPatients, mPatients * 1.2f, mVaccinations, mRevenue)) // Simulated Consultations as 1.2x Patients
        }
    }

    OverviewContent(
        availableYears = availableYears,
        filterMode = filterMode,
        fyQuarter = fyQuarter,
        selectedMonth = selectedMonth,
        onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
        onQuarterChange = { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 },
        onMonthChange = { selectedMonth = if (selectedMonth == it) -1 else it },
        currentStats = currentFinanceStats,
        prevStats = prevFinanceStats,
        patientsCount = filteredPatients.size,
        prevPatientsCount = prevPatients.size,
        vaccPatientsCount = filteredVaccinations.map { it.patientId }.distinct().size,
        prevVaccPatientsCount = prevVaccinations.map { it.patientId }.distinct().size,
        dosesCount = filteredVaccinations.sumOf { v -> v.items.sumOf { it.quantity.coerceAtLeast(0) } },
        prevDosesCount = prevVaccinations.sumOf { v -> v.items.sumOf { it.quantity.coerceAtLeast(0) } },
        trendData = trendData
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewContent(
    availableYears: List<String>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    currentStats: com.neochildclinic.features.statistics.FinanceStatsData,
    prevStats: com.neochildclinic.features.statistics.FinanceStatsData,
    patientsCount: Int,
    prevPatientsCount: Int,
    vaccPatientsCount: Int,
    prevVaccPatientsCount: Int,
    dosesCount: Int,
    prevDosesCount: Int,
    trendData: List<ChartDataPoint>
) {
    val customColors = LocalCustomColors.current
    val fyOptions = remember(availableYears) { availableYears.reversed().map { "20$it" } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            FilterSection(
                availableYears = fyOptions,
                filterMode = filterMode,
                fyQuarter = fyQuarter,
                selectedMonth = selectedMonth,
                onFilterModeChange = { onFilterModeChange("FY ${it.takeLast(5)}") },
                onQuarterChange = onQuarterChange,
                onMonthChange = onMonthChange
            )
        }

        item {
            Text(
                "Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Patients",
                    value = String.format(Locale.US, "%,d", patientsCount),
                    icon = Icons.Default.Person,
                    iconColor = customColors.textBlue,
                    iconBackground = customColors.softBlue,
                    growthPercentage = StatisticsUtils.calculateGrowth(patientsCount.toDouble(), prevPatientsCount.toDouble())
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "New Patients",
                    value = String.format(Locale.US, "%,d", patientsCount), // Assuming new patients = registered in period
                    icon = Icons.Default.PersonAdd,
                    iconColor = customColors.textGreen,
                    iconBackground = customColors.softGreen,
                    growthPercentage = StatisticsUtils.calculateGrowth(patientsCount.toDouble(), prevPatientsCount.toDouble())
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Consultations",
                    value = String.format(Locale.US, "%,d", (patientsCount * 1.2).toInt()), // Simulated
                    icon = Icons.Default.MedicalServices,
                    iconColor = customColors.textPurple,
                    iconBackground = customColors.softPurple,
                    growthPercentage = StatisticsUtils.calculateGrowth(patientsCount * 1.2, prevPatientsCount * 1.2)
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Vaccinated Patients",
                    value = String.format(Locale.US, "%,d", vaccPatientsCount),
                    icon = Icons.Default.VerifiedUser,
                    iconColor = customColors.textCyan,
                    iconBackground = customColors.softCyan,
                    growthPercentage = StatisticsUtils.calculateGrowth(vaccPatientsCount.toDouble(), prevVaccPatientsCount.toDouble())
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Doses",
                    value = String.format(Locale.US, "%,d", dosesCount),
                    icon = Icons.Default.Vaccines,
                    iconColor = customColors.textOrange,
                    iconBackground = customColors.softOrange,
                    growthPercentage = StatisticsUtils.calculateGrowth(dosesCount.toDouble(), prevDosesCount.toDouble())
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Revenue",
                    value = String.format(Locale.US, "₹%,.0f", currentStats.totalRevenue),
                    icon = Icons.Default.CurrencyRupee,
                    iconColor = customColors.textBlue,
                    iconBackground = customColors.softBlue,
                    growthPercentage = if (filterMode == "Overall") null else StatisticsUtils.calculateGrowth(currentStats.totalRevenue, prevStats.totalRevenue)
                )
            }
        }

        item {
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
        }

        item {
            SummaryCard(
                modifier = Modifier.fillMaxWidth(),
                title = "Net Profit",
                value = if (currentStats.isProfitComplete) String.format(Locale.US, "₹%,.0f", currentStats.netProfit) else "Unavailable",
                icon = Icons.Default.TrendingUp,
                iconColor = customColors.textPink,
                iconBackground = customColors.softPink,
                growthPercentage = if (filterMode == "Overall" || !currentStats.isProfitComplete || !prevStats.isProfitComplete) null else StatisticsUtils.calculateGrowth(currentStats.netProfit, prevStats.netProfit)
            )
        }

        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Quick Overview",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                TrendChart(
                    title = "Monthly Trend (Last 6 Months)",
                    data = trendData,
                    seriesLabels = listOf("Patients", "Consultations", "Vaccinations", "Revenue (₹K)"),
                    seriesColors = listOf(ChartPatients, ChartConsultations, ChartVaccinations, ChartRevenue)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { /* Navigate to full report */ },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "View full report",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
