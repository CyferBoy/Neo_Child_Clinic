package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.statistics.domain.FinanceCalculator
import com.neochildclinic.feature.statistics.domain.FinanceStatsData
import com.neochildclinic.feature.statistics.domain.StatisticsDateUtils
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import com.neochildclinic.feature.statistics.domain.VisitTypeStats
import java.time.YearMonth
import java.util.Locale

@Composable
fun OverviewTab(
    patients: List<Patient>,
    vaccinations: List<Vaccination>,
    financeTransactions: List<FinanceTransaction>,
    showFilters: Boolean = false,
    onFullReportClick: () -> Unit = {}
) {
    var filterMode by rememberSaveable { mutableStateOf(StatisticsUtils.getCurrentFinancialYear()) }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val availableYears = remember(patients, vaccinations, financeTransactions) {
        StatisticsUtils.getAvailableFinancialYears(
            patients.map { it.registrationDate ?: "" } +
                    vaccinations.map { it.dateGiven } +
                    financeTransactions.map { FinanceCalculator.resolveReportingDate(it) }
        )
    }

    // Pre-compute effective registration dates for all patients. Raw visits: a patient's first
    // contact may be an ACTIVE-status consultation, so the list matches PatientsTab's input.
    val effectiveRegDates = remember(patients, vaccinations) {
        StatisticsDateUtils.computeEffectiveRegistrationDates(patients, vaccinations)
    }

    // Current period data - patients use effective registration date
    val filteredPatients = remember(patients, effectiveRegDates, filterMode, fyQuarter, selectedMonth) {
        patients.filter { StatisticsUtils.isEffectiveDateInFilter(effectiveRegDates[it.id], filterMode, fyQuarter, selectedMonth) }
    }
    val filteredVaccinations = remember(vaccinations, filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.filterValidVaccinations(vaccinations).filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }
    val filteredTransactions = remember(financeTransactions, filterMode, fyQuarter, selectedMonth) {
        financeTransactions.filter { StatisticsUtils.isDateInFilter(FinanceCalculator.resolveReportingDate(it), filterMode, fyQuarter, selectedMonth) }
    }

    // Previous period data for growth calculation - skipped when Overall (no meaningful comparison)
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

    // Visit-type counts (raw, non-deleted visits, by visit_type) for the three Overview cards.
    val visitTypeStatsCurrent = remember(vaccinations, filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.visitTypeStatsForFilter(vaccinations, filterMode, fyQuarter, selectedMonth)
    }
    val visitTypeStatsPrev = remember(vaccinations, prevFilter, prevQuarter, prevMonth, isOverall) {
        if (isOverall) VisitTypeStats(0, 0, 0) else StatisticsUtils.visitTypeStatsForFilter(vaccinations, prevFilter, prevQuarter, prevMonth)
    }

    // Quick Overview Chart Data - Patient Activity (last 6 months, filter-aware)
    val allValidVaccinations = remember(vaccinations) { StatisticsUtils.filterValidVaccinations(vaccinations) }

    val currentFinanceStats = remember(filteredTransactions, allValidVaccinations, filteredVaccinations) {
        FinanceCalculator.calculateFinanceStats(filteredTransactions, allValidVaccinations, financeTransactions, filteredVaccinations)
    }
    val prevFinanceStats = remember(prevTransactions, allValidVaccinations, prevVaccinations, isOverall) {
        if (isOverall) FinanceStatsData(totalRevenue = 0.0, cashTotal = 0.0, onlineTotal = 0.0, totalExpenses = 0.0, vaccineCost = 0.0, grossProfit = 0.0, netProfit = 0.0)
        else FinanceCalculator.calculateFinanceStats(prevTransactions, allValidVaccinations, financeTransactions, prevVaccinations)
    }

    val patientActivityData = remember(patients, vaccinations, financeTransactions, allValidVaccinations, filterMode, fyQuarter, selectedMonth) {
        val months = StatisticsDateUtils.lastNMonths(6)
        val monthKeys = months.toSet()

        val patientCounts = patients.asSequence()
            .mapNotNull { effectiveRegDates[it.id]?.let { ed -> YearMonth.from(ed) } }
            .filter { it in monthKeys }
            .groupingBy { it }.eachCount()

        // Raw visits: consultation visits are recorded ACTIVE and are never filterValidVaccinations-gated.
        val consultationCounts = vaccinations.asSequence()
            .filter { it.visitType.equals("CONSULTATION", ignoreCase = true) }
            .mapNotNull { StatisticsDateUtils.monthOfIST(it.dateGiven) }
            .filter { it in monthKeys }
            .groupingBy { it }.eachCount()

        val vaccinationCounts = allValidVaccinations.asSequence()
            .filter { it.visitType.equals("VACCINATION", ignoreCase = true) }
            .mapNotNull { StatisticsDateUtils.monthOfIST(it.dateGiven) }
            .filter { it in monthKeys }
            .groupingBy { it }.eachCount()

        months.map { month ->
            ChartDataPoint(StatisticsUtils.monthNames[month.monthValue - 1], listOf(
                (patientCounts[month] ?: 0).toFloat(),
                (consultationCounts[month] ?: 0).toFloat(),
                (vaccinationCounts[month] ?: 0).toFloat()
            ))
        }
    }

    // Quick Overview Chart Data - Financial Trend (last 6 months, filter-aware)
    val financialTrendData = remember(financeTransactions, filterMode, fyQuarter, selectedMonth) {
        val months = StatisticsDateUtils.lastNMonths(6)
        val monthKeys = months.toSet()

        data class MonthFinance(val revenue: Double, val cash: Double, val online: Double)

        val financeByMonth = mutableMapOf<YearMonth, MonthFinance>()
        // Shared rule: duplicate vaccination income per visit is counted once, so this trend
        // agrees with Financial Statistics and Full Report.
        FinanceCalculator.effectiveIncome(financeTransactions).forEach { tx ->
            val key = StatisticsDateUtils.monthOfIST(FinanceCalculator.resolveReportingDate(tx)) ?: return@forEach
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

        months.map { month ->
            val mf = financeByMonth[month] ?: MonthFinance(0.0, 0.0, 0.0)
            ChartDataPoint(StatisticsUtils.monthNames[month.monthValue - 1], listOf(
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
        showFilters = showFilters,
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
    currentStats: FinanceStatsData,
    prevStats: FinanceStatsData,
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
    showFilters: Boolean = false,
    onFullReportClick: () -> Unit
) {
    val customColors = LocalCustomColors.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val fyOptions = remember(availableYears) { availableYears.reversed().map { "20$it" } }
    val overall = filterMode == "Overall"

    val patientColor = if (darkTheme) ChartPatientsDark else ChartPatients
    val consultationColor = if (darkTheme) ChartConsultationsDark else ChartConsultations
    val vaccinationColor = if (darkTheme) ChartVaccinationsDark else ChartVaccinations
    val revenueColor = if (darkTheme) ChartRevenueDark else ChartRevenue
    val onlineColor = if (darkTheme) ChartOnlineDark else ChartOnline
    val cashColor = if (darkTheme) ChartCashDark else ChartCash

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.lg,
            bottom = Spacing.xxl
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
    ) {
        if (showFilters) {
            item {
                FilterSection(
                    availableYears = fyOptions,
                    filterMode = filterMode,
                    fyQuarter = fyQuarter,
                    selectedMonth = selectedMonth,
                    onFilterModeChange = onFilterModeChange,
                    onQuarterChange = onQuarterChange,
                    onMonthChange = onMonthChange
                )
            }
        }

        item {
            StatisticsSectionHeader(
                title = "Summary",
                subtitle = "Selected period at a glance"
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MetricRow {
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Total Patients",
                        value = String.format(Locale.US, "%,d", patientsCount),
                        accentColor = MaterialTheme.colorScheme.primary,
                        supportingText = "in selected period",
                        growthPercentage = StatisticsUtils.calculateGrowth(patientsCount.toDouble(), prevPatientsCount.toDouble())
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "New Registrations",
                        value = String.format(Locale.US, "%,d", patientsCount),
                        accentColor = customColors.textSuccess,
                        supportingText = "effective registration date",
                        growthPercentage = StatisticsUtils.calculateGrowth(patientsCount.toDouble(), prevPatientsCount.toDouble())
                    )
                }
                MetricRow {
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Consulted Patients",
                        value = String.format(Locale.US, "%,d", consultedPatientsCount),
                        accentColor = customColors.textPurple,
                        supportingText = "unique patients",
                        growthPercentage = StatisticsUtils.calculateGrowth(consultedPatientsCount.toDouble(), prevConsultedPatientsCount.toDouble())
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Vaccinated Patients",
                        value = String.format(Locale.US, "%,d", vaccPatientsCount),
                        accentColor = customColors.textOrange,
                        supportingText = "unique patients",
                        growthPercentage = StatisticsUtils.calculateGrowth(vaccPatientsCount.toDouble(), prevVaccPatientsCount.toDouble())
                    )
                }
                MetricRow {
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Total Consultation",
                        value = String.format(Locale.US, "%,d", totalConsultationCount),
                        accentColor = customColors.textBlue,
                        supportingText = "consultation visits",
                        growthPercentage = StatisticsUtils.calculateGrowth(totalConsultationCount.toDouble(), prevTotalConsultationCount.toDouble())
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Total Vaccination",
                        value = String.format(Locale.US, "%,d", totalVaccinationCount),
                        accentColor = customColors.textOrange,
                        supportingText = "vaccination visits",
                        growthPercentage = StatisticsUtils.calculateGrowth(totalVaccinationCount.toDouble(), prevTotalVaccinationCount.toDouble())
                    )
                }
                SummaryCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Total Doses",
                    value = String.format(Locale.US, "%,d", dosesCount),
                    accentColor = MaterialTheme.colorScheme.primary,
                    supportingText = "administered doses in the selected period",
                    growthPercentage = StatisticsUtils.calculateGrowth(dosesCount.toDouble(), prevDosesCount.toDouble())
                )
            }
        }

        item {
            StatisticsSectionHeader(
                title = "Finance",
                subtitle = "Payments and profitability"
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                MetricRow {
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Revenue",
                        value = StatisticsUtils.formatRupees(currentStats.totalRevenue),
                        accentColor = MaterialTheme.colorScheme.primary,
                        supportingText = "vaccination + consultation",
                        growthPercentage = if (overall) null else StatisticsUtils.calculateGrowth(currentStats.totalRevenue, prevStats.totalRevenue)
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Net Profit",
                        value = if (currentStats.isProfitComplete) StatisticsUtils.formatRupees(currentStats.netProfit) else "Unavailable",
                        accentColor = when {
                            !currentStats.isProfitComplete -> MaterialTheme.colorScheme.onSurfaceVariant
                            currentStats.netProfit >= 0 -> customColors.textSuccess
                            else -> MaterialTheme.colorScheme.error
                        },
                        supportingText = if (currentStats.isProfitComplete) "after COGS and expenses" else "missing stock cost history",
                        growthPercentage = if (overall || !currentStats.isProfitComplete || !prevStats.isProfitComplete) {
                            null
                        } else {
                            StatisticsUtils.calculateGrowth(currentStats.netProfit, prevStats.netProfit)
                        }
                    )
                }
                MetricRow {
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Cash",
                        value = StatisticsUtils.formatRupees(currentStats.cashTotal),
                        accentColor = customColors.textSuccess,
                        supportingText = "cash payments",
                        growthPercentage = if (overall) null else StatisticsUtils.calculateGrowth(currentStats.cashTotal, prevStats.cashTotal)
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        title = "Online",
                        value = StatisticsUtils.formatRupees(currentStats.onlineTotal),
                        accentColor = customColors.textBlue,
                        supportingText = "online payments",
                        growthPercentage = if (overall) null else StatisticsUtils.calculateGrowth(currentStats.onlineTotal, prevStats.onlineTotal)
                    )
                }
            }
        }

        item {
            StatisticsSectionHeader(
                title = "Trends",
                subtitle = "Last 6 months"
            )
        }

        item {
            TrendChart(
                title = "Financial trend",
                subtitle = "Revenue, online and cash (\u20b9K)",
                data = financialTrendData,
                seriesLabels = listOf("Revenue", "Online", "Cash"),
                seriesColors = listOf(revenueColor, onlineColor, cashColor)
            )
        }

        item {
            TrendChart(
                title = "Patient activity",
                subtitle = "Registrations, consultations and vaccinations",
                data = patientActivityData,
                seriesLabels = listOf("Patients", "Consultations", "Vaccinations"),
                seriesColors = listOf(patientColor, consultationColor, vaccinationColor)
            )
        }

        item {
            OutlinedButton(
                onClick = onFullReportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = "View full report",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        item {
            Text(
                text = "Data refreshes from local records. Offline changes are included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MetricRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        content = content
    )
}
