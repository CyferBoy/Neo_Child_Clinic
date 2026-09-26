package com.neochildclinic.features.statistics
import com.neochildclinic.domain.statistics.FinanceStatsData
import com.neochildclinic.domain.statistics.StatisticsDateUtils
import com.neochildclinic.domain.statistics.VisitTypeStats
import com.neochildclinic.domain.statistics.StatisticsUtils
import com.neochildclinic.domain.statistics.FinanceCalculator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.core.designsystem.*
import java.util.*

@Composable
fun OverviewTab(
    patients: List<Patient>,
    vaccinations: List<Vaccination>,
    financeTransactions: List<FinanceEntity>,
    onFullReportClick: () -> Unit = {}
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

    // Pre-compute effective registration dates for all patients
    val effectiveRegDates = remember(patients, vaccinations) {
        StatisticsDateUtils.computeEffectiveRegistrationDates(patients, StatisticsUtils.filterValidVaccinations(vaccinations))
    }

    // Current period data — patients use effective registration date
    val filteredPatients = remember(patients, effectiveRegDates, filterMode, fyQuarter, selectedMonth) {
        patients.filter { StatisticsUtils.isEffectiveDateInFilter(effectiveRegDates[it.id], filterMode, fyQuarter, selectedMonth) }
    }
    val filteredVaccinations = remember(vaccinations, filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.filterValidVaccinations(vaccinations).filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }
    val filteredTransactions = remember(financeTransactions, filterMode, fyQuarter, selectedMonth) {
        financeTransactions.filter { StatisticsUtils.isDateInFilter(FinanceCalculator.resolveReportingDate(it), filterMode, fyQuarter, selectedMonth) }
    }

    // Previous period data for growth calculation — skipped when Overall (no meaningful comparison)
    val isOverall = filterMode == "Overall"
    val (prevFilter, prevQuarter, prevMonth) = remember(filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.getPreviousPeriodFilter(filterMode, fyQuarter, selectedMonth)
    }
    val prevPatients = remember(patients, effectiveRegDates, prevFilter, prevQuarter, prevMonth, isOverall) {
        if (isOverall) emptyList() else patients.filter { StatisticsUtils.isEffectiveDateInFilter(effectiveRegDates[it.id], prevFilter, prevQuarter, prevMonth) }
    }
    val prevVaccinations = remember(vaccinations, prevFilter, prevQuarter, prevMonth, isOverall) {
        if (isOverall) emptyList() else StatisticsUtils.filterValidVaccinations(vaccinations).filter { StatisticsUtils.isDateInFilter(it.dateGiven, prevFilter, prevQuarter, prevMonth) }
    }
    val prevTransactions = remember(financeTransactions, prevFilter, prevQuarter, prevMonth, isOverall) {
        if (isOverall) emptyList() else financeTransactions.filter { StatisticsUtils.isDateInFilter(FinanceCalculator.resolveReportingDate(it), prevFilter, prevQuarter, prevMonth) }
    }

    // Visit-type counts (all non-deleted visits, by visit_type) for the three Overview cards.
    val visitTypeStatsCurrent = remember(vaccinations, filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.visitTypeStats(vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) })
    }
    val visitTypeStatsPrev = remember(vaccinations, prevFilter, prevQuarter, prevMonth, isOverall) {
        if (isOverall) VisitTypeStats(0, 0, 0) else StatisticsUtils.visitTypeStats(vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, prevFilter, prevQuarter, prevMonth) })
    }

    // Quick Overview Chart Data — Patient Activity (last 6 months, filter-aware)
    val allValidVaccinations = remember(vaccinations) { StatisticsUtils.filterValidVaccinations(vaccinations) }

    val currentFinanceStats = remember(filteredTransactions, allValidVaccinations, filteredVaccinations) {
        FinanceCalculator.calculateFinanceStats(filteredTransactions, allValidVaccinations, financeTransactions, filteredVaccinations)
    }
    val prevFinanceStats = remember(prevTransactions, allValidVaccinations, prevVaccinations, isOverall) {
        if (isOverall) FinanceStatsData(totalRevenue = 0.0, cashTotal = 0.0, onlineTotal = 0.0, totalExpenses = 0.0, vaccineCost = 0.0, grossProfit = 0.0, netProfit = 0.0)
        else FinanceCalculator.calculateFinanceStats(prevTransactions, allValidVaccinations, financeTransactions, prevVaccinations)
    }

    val patientActivityData = remember(patients, vaccinations, financeTransactions, allValidVaccinations, filterMode, fyQuarter, selectedMonth) {
        val (curYear, curMonth) = StatisticsDateUtils.currentISTYearMonth()
        val months = (0 until 6).reversed().map { monthOffset ->
            var m = curMonth - monthOffset
            var y = curYear
            while (m < 0) { m += 12; y -= 1 }
            val key = y * 12 + m
            key to StatisticsUtils.monthNames[m]
        }
        val monthKeys = months.map { it.first }.toSet()

        val patientCounts = patients.asSequence()
            .mapNotNull { effectiveRegDates[it.id]?.let { ed -> ed.year * 12 + (ed.monthValue - 1) } }
            .filter { it in monthKeys }
            .groupingBy { it }.eachCount()

        val consultationCounts = allValidVaccinations.asSequence()
            .filter { it.visitType.equals("CONSULTATION", ignoreCase = true) }
            .mapNotNull { StatisticsDateUtils.monthKeyIST(it.dateGiven) }
            .filter { it in monthKeys }
            .groupingBy { it }.eachCount()

        val vaccinationCounts = allValidVaccinations.asSequence()
            .filter { it.visitType.equals("VACCINATION", ignoreCase = true) }
            .mapNotNull { StatisticsDateUtils.monthKeyIST(it.dateGiven) }
            .filter { it in monthKeys }
            .groupingBy { it }.eachCount()

        months.map { (key, monthLabel) ->
            ChartDataPoint(monthLabel, listOf(
                (patientCounts[key] ?: 0).toFloat(),
                (consultationCounts[key] ?: 0).toFloat(),
                (vaccinationCounts[key] ?: 0).toFloat()
            ))
        }
    }

    // Quick Overview Chart Data — Financial Trend (last 6 months, filter-aware)
    val financialTrendData = remember(financeTransactions, filterMode, fyQuarter, selectedMonth) {
        val (curYear, curMonth) = StatisticsDateUtils.currentISTYearMonth()
        val months = (0 until 6).reversed().map { monthOffset ->
            var m = curMonth - monthOffset
            var y = curYear
            while (m < 0) { m += 12; y -= 1 }
            val key = y * 12 + m
            key to StatisticsUtils.monthNames[m]
        }
        val monthKeys = months.map { it.first }.toSet()

        data class MonthFinance(val revenue: Double, val cash: Double, val online: Double)

        val financeByMonth = mutableMapOf<Int, MonthFinance>()
        financeTransactions.forEach { tx ->
            val key = StatisticsDateUtils.monthKeyIST(FinanceCalculator.resolveReportingDate(tx)) ?: return@forEach
            if (key !in monthKeys) return@forEach
            if (!tx.type.equals("INCOME", true)) return@forEach
            val existing = financeByMonth.getOrDefault(key, MonthFinance(0.0, 0.0, 0.0))
            val amount = tx.amount.coerceAtLeast(0.0)
            val (cashAmt, onlineAmt) = FinanceCalculator.cashAndOnlineOf(tx)
            financeByMonth[key] = existing.copy(
                revenue = existing.revenue + amount,
                cash = existing.cash + cashAmt,
                online = existing.online + onlineAmt
            )
        }

        months.map { (key, monthLabel) ->
            val mf = financeByMonth[key] ?: MonthFinance(0.0, 0.0, 0.0)
            ChartDataPoint(monthLabel, listOf(
                (mf.revenue / 1000.0).toFloat(),
                (mf.online / 1000.0).toFloat(),
                (mf.cash / 1000.0).toFloat()
            ))
        }
    }

    OverviewContent(
        availableYears = availableYears,
        filterMode = filterMode,
        fyQuarter = fyQuarter,
        selectedMonth = selectedMonth,
        onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
        onQuarterChange = { if (filterMode != "Overall") { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 } },
        onMonthChange = { if (fyQuarter != 0 && filterMode != "Overall") { selectedMonth = if (selectedMonth == it) -1 else it } },
        currentStats = currentFinanceStats,
        prevStats = prevFinanceStats,
        patientsCount = filteredPatients.size,
        prevPatientsCount = prevPatients.size,
        vaccPatientsCount = filteredVaccinations.map { it.patientId }.distinct().size,
        prevVaccPatientsCount = prevVaccinations.map { it.patientId }.distinct().size,
        consultedPatientsCount = visitTypeStatsCurrent.consultedPatients,
        prevConsultedPatientsCount = visitTypeStatsPrev.consultedPatients,
        totalVaccinationCount = visitTypeStatsCurrent.totalVaccination,
        prevTotalVaccinationCount = visitTypeStatsPrev.totalVaccination,
        totalConsultationCount = visitTypeStatsCurrent.totalConsultation,
        prevTotalConsultationCount = visitTypeStatsPrev.totalConsultation,
        dosesCount = filteredVaccinations.sumOf { v -> v.items.sumOf { it.quantity.coerceAtLeast(0) } },
        prevDosesCount = prevVaccinations.sumOf { v -> v.items.sumOf { it.quantity.coerceAtLeast(0) } },
        patientActivityData = patientActivityData,
        financialTrendData = financialTrendData,
        onFullReportClick = onFullReportClick
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
    currentStats: com.neochildclinic.domain.statistics.FinanceStatsData,
    prevStats: com.neochildclinic.domain.statistics.FinanceStatsData,
    patientsCount: Int,
    prevPatientsCount: Int,
    vaccPatientsCount: Int,
    prevVaccPatientsCount: Int,
    consultedPatientsCount: Int,
    prevConsultedPatientsCount: Int,
    totalVaccinationCount: Int,
    prevTotalVaccinationCount: Int,
    totalConsultationCount: Int,
    prevTotalConsultationCount: Int,
    dosesCount: Int,
    prevDosesCount: Int,
    patientActivityData: List<ChartDataPoint>,
    financialTrendData: List<ChartDataPoint>,
    onFullReportClick: () -> Unit
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
                onFilterModeChange = { onFilterModeChange(it) },
                onQuarterChange = onQuarterChange,
                onMonthChange = onMonthChange,
                modifier = Modifier.padding(top = 16.dp)
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
                    title = "Consulted Patients",
                    value = String.format(Locale.US, "%,d", consultedPatientsCount),
                    icon = Icons.Default.Groups,
                    iconColor = customColors.textPurple,
                    iconBackground = customColors.softPurple,
                    growthPercentage = StatisticsUtils.calculateGrowth(consultedPatientsCount.toDouble(), prevConsultedPatientsCount.toDouble())
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
                    title = "Total Consultation",
                    value = String.format(Locale.US, "%,d", totalConsultationCount),
                    icon = Icons.Default.MedicalServices,
                    iconColor = customColors.textPink,
                    iconBackground = customColors.softPink,
                    growthPercentage = StatisticsUtils.calculateGrowth(totalConsultationCount.toDouble(), prevTotalConsultationCount.toDouble())
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Total Vaccination",
                    value = String.format(Locale.US, "%,d", totalVaccinationCount),
                    icon = Icons.Default.Vaccines,
                    iconColor = customColors.textOrange,
                    iconBackground = customColors.softOrange,
                    growthPercentage = StatisticsUtils.calculateGrowth(totalVaccinationCount.toDouble(), prevTotalVaccinationCount.toDouble())
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
                    value = StatisticsUtils.formatRupees(currentStats.totalRevenue),
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
                    value = StatisticsUtils.formatRupees(currentStats.cashTotal),
                    icon = Icons.Default.Payments,
                    iconColor = customColors.textGreen,
                    iconBackground = customColors.softGreen,
                    growthPercentage = if (filterMode == "Overall") null else StatisticsUtils.calculateGrowth(currentStats.cashTotal, prevStats.cashTotal)
                )
                SummaryCard(
                    modifier = Modifier.weight(1f),
                    title = "Online",
                    value = StatisticsUtils.formatRupees(currentStats.onlineTotal),
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
                value = if (currentStats.isProfitComplete) StatisticsUtils.formatRupees(currentStats.netProfit) else "Unavailable",
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
                    title = "Patient Activity (Last 6 Months)",
                    data = patientActivityData,
                    seriesLabels = listOf("Patients", "Consultations", "Vaccinations"),
                    seriesColors = listOf(ChartPatients, ChartConsultations, ChartVaccinations)
                )
                Spacer(modifier = Modifier.height(16.dp))
                TrendChart(
                    title = "Financial Trend (Last 6 Months, ₹K)",
                    data = financialTrendData,
                    seriesLabels = listOf("Revenue", "Online", "Cash"),
                    seriesColors = listOf(ChartRevenue, ChartOnline, ChartCash)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onFullReportClick() },
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


