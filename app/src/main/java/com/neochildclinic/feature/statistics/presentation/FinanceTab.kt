package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.statistics.domain.ExpenseCalculator
import com.neochildclinic.feature.statistics.domain.FinanceCalculator
import com.neochildclinic.feature.statistics.domain.FinanceStatsData
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FinanceTab(
    vaccinations: List<Vaccination>,
    transactions: List<FinanceTransaction>,
    expenses: List<Expense> = emptyList(),
    showFilters: Boolean = false,
    onMonthClick: (String) -> Unit = {}
) {
    var filterMode by rememberSaveable { mutableStateOf(StatisticsUtils.getCurrentFinancialYear()) }
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
        onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
        onQuarterChange = { if (filterMode != "Overall") { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 } },
        onMonthChange = { if (fyQuarter != 0 && filterMode != "Overall") { selectedMonth = if (selectedMonth == it) -1 else it } },
        onMonthClick = onMonthClick,
        vaccinations = vaccinations,
        currentExpensesRupees = currentExpensesPaise / 100.0,
        prevExpensesRupees = prevExpensesPaise / 100.0,
        netIncome = netIncome,
        prevNetIncome = prevNetIncome,
        visitDatesById = visitDatesById,
        expenses = expenses,
        showFilters = showFilters
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceContent(
    currentStats: FinanceStatsData,
    prevStats: FinanceStatsData,
    filterMode: String,
    availableYears: List<String>,
    transactions: List<FinanceTransaction>,
    filteredTransactions: List<FinanceTransaction>,
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
    visitDatesById: Map<String, String>? = null,
    expenses: List<Expense> = emptyList(),
    showFilters: Boolean = false
) {
    val customColors = LocalCustomColors.current
    val overall = filterMode == "Overall"
    val cashShare = if (currentStats.totalRevenue == 0.0) {
        null
    } else {
        (currentStats.cashTotal / currentStats.totalRevenue * 100).roundToInt()
    }
    val onlineShare = if (currentStats.totalRevenue == 0.0) {
        null
    } else {
        (currentStats.onlineTotal / currentStats.totalRevenue * 100).roundToInt()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
    ) {
        if (showFilters) {
            FilterSection(
                availableYears = availableYears.reversed().map { "20$it" },
                filterMode = filterMode,
                fyQuarter = fyQuarter,
                selectedMonth = selectedMonth,
                onFilterModeChange = onFilterModeChange,
                onQuarterChange = onQuarterChange,
                onMonthChange = onMonthChange
            )
            Spacer(modifier = Modifier.height(Spacing.xl))
        }

        // Surfaces FinanceStatsData.invalidTransactionDateCount, which was already being
        // computed but never shown anywhere - a transaction whose date/timestamp genuinely
        // can't be parsed silently disappears from every total (including Overall) with no
        // indication to the user. This makes that visible instead of leaving totals looking
        // mysteriously low with no way to tell why.
        if (currentStats.invalidTransactionDateCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.cardDense),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "${currentStats.invalidTransactionDateCount} transaction(s) have a date that couldn't be read and are excluded from every total shown here.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
        }

        NetProfitHero(
            stats = currentStats,
            previousStats = prevStats,
            filterMode = filterMode,
            currentExpensesRupees = currentExpensesRupees,
            prevExpensesRupees = prevExpensesRupees
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Cash",
                value = StatisticsUtils.formatRupees(currentStats.cashTotal),
                accentColor = customColors.textSuccess,
                supportingText = cashShare?.let { "$it% of revenue" } ?: "cash payments",
                growthPercentage = if (overall) null else StatisticsUtils.calculateGrowth(currentStats.cashTotal, prevStats.cashTotal)
            )
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Online",
                value = StatisticsUtils.formatRupees(currentStats.onlineTotal),
                accentColor = customColors.textBlue,
                supportingText = onlineShare?.let { "$it% of revenue" } ?: "online payments",
                growthPercentage = if (overall) null else StatisticsUtils.calculateGrowth(currentStats.onlineTotal, prevStats.onlineTotal)
            )
        }

        if (!currentStats.isProfitComplete) {
            Spacer(modifier = Modifier.height(Spacing.md))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.cardDense),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Column {
                        Text(
                            "Incomplete profit calculation",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Some vaccination records are missing historical stock cost data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        StatisticsSectionHeader(
            title = "Expenses",
            subtitle = "Separate expense records and net income"
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Total Expenses",
                value = StatisticsUtils.formatRupees(currentExpensesRupees),
                accentColor = customColors.textPink,
                supportingText = "expense records",
                growthPercentage = if (overall) null else StatisticsUtils.calculateGrowth(currentExpensesRupees, prevExpensesRupees)
            )
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Net Income",
                value = StatisticsUtils.formatRupees(netIncome),
                accentColor = if (netIncome >= 0) customColors.textSuccess else MaterialTheme.colorScheme.error,
                supportingText = "revenue minus expenses",
                growthPercentage = if (overall) null else StatisticsUtils.calculateGrowth(netIncome, prevNetIncome)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        StatisticsSectionHeader(
            title = "Monthly performance",
            subtitle = "Tap a month for its transactions"
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        FinanceTable(
            transactions = filteredTransactions,
            vaccinations = vaccinations,
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onMonthClick = onMonthClick,
            visitDatesById = visitDatesById,
            expenses = expenses
        )

        Spacer(modifier = Modifier.height(Spacing.xxl))
    }
}

@Composable
private fun NetProfitHero(
    stats: FinanceStatsData,
    previousStats: FinanceStatsData,
    filterMode: String,
    currentExpensesRupees: Double = 0.0,
    prevExpensesRupees: Double = 0.0
) {
    val currentNetProfit = stats.netProfit - currentExpensesRupees
    val previousNetProfit = previousStats.netProfit - prevExpensesRupees
    val growth = if (
        filterMode == "Overall" ||
        !stats.isProfitComplete ||
        !previousStats.isProfitComplete
    ) {
        null
    } else {
        StatisticsUtils.calculateGrowth(currentNetProfit, previousNetProfit)
    }
    val revenue = StatisticsUtils.formatRupees(stats.totalRevenue)
    val costs = StatisticsUtils.formatRupees(stats.vaccineCost + stats.totalExpenses + currentExpensesRupees)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ChartContainerShape,
        color = ClinicBlue,
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Text(
                text = "NET PROFIT",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                color = Color.White.copy(alpha = 0.78f)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = if (stats.isProfitComplete) StatisticsUtils.formatRupees(currentNetProfit) else "Unavailable",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1
            )
            if (growth != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (growth >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f%% vs previous period", kotlin.math.abs(growth)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
            Spacer(modifier = Modifier.height(Spacing.md))
            Row(modifier = Modifier.fillMaxWidth()) {
                HeroMetric(
                    label = "Revenue",
                    value = revenue,
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    label = "COGS + expense",
                    value = costs,
                    modifier = Modifier.weight(1f),
                    alignEnd = true
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.7.sp),
            color = Color.White.copy(alpha = 0.72f),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 1
        )
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
