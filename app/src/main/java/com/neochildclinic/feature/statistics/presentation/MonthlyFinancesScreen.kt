package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.feature.statistics.domain.FinanceCalculator
import com.neochildclinic.feature.statistics.domain.FinanceSummaryItem
import com.neochildclinic.feature.statistics.domain.StatisticsUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyFinancesScreen(
    onBack: () -> Unit,
    onMonthClick: (String) -> Unit,
    viewModel: FinanceDetailsViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val vaccinations by viewModel.vaccinations.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var selectedFilter by rememberSaveable { mutableStateOf("Overall") }

    val validPatients = remember(patients) { patients.filter { it.name.isNotBlank() } }
    val patientIds = remember(validPatients) { validPatients.map { it.id }.toSet() }

    val validVaccinations = remember(vaccinations) {
        StatisticsUtils.filterValidVaccinations(vaccinations)
    }
    val validVaccinationIds = remember(validVaccinations) {
        validVaccinations.map { it.id }.toSet()
    }

    val cleanTransactions = remember(transactions, validVaccinationIds, patientIds) {
        transactions.filter { tx ->
            FinanceCalculator.isCleanTransaction(tx, validVaccinationIds, patientIds)
        }
    }

    val cleanExpenses = remember(expenses) {
        expenses.filter { FinanceCalculator.isCleanExpense(it) }
    }

    val visitDatesById = remember(validVaccinations) { validVaccinations.associate { it.id to it.dateGiven } }

    val transactionsWithDates = remember(cleanTransactions, visitDatesById) {
        cleanTransactions.map { it to FinanceCalculator.resolveReportingDate(it, visitDatesById) }
    }

    val availableYears = remember(transactionsWithDates, validVaccinations) {
        StatisticsUtils.getAvailableFinancialYears(
            transactionsWithDates.map { it.second } + validVaccinations.map { it.dateGiven }
        )
    }

    val filterOptions = remember(availableYears) {
        listOf("Overall") + availableYears.reversed().map { "FY $it" }
    }

    val displayData = remember(cleanTransactions, validVaccinations, cleanExpenses, selectedFilter, visitDatesById) {
        FinanceCalculator.getMonthlyGroupedData(
            transactions = cleanTransactions,
            vaccinations = validVaccinations,
            filterMode = selectedFilter,
            selectedQuarter = 0,
            selectedMonth = -1,
            visitDatesById = visitDatesById,
            expenses = cleanExpenses
        ).filter { it.revenue > 0.0 || it.expenses > 0.0 }.reversed()
    }

    val totalPeriodRevenue = remember(displayData) { displayData.sumOf { it.revenue } }
    val totalPeriodExpenses = remember(displayData) { displayData.sumOf { it.expenses } }
    val totalPeriodNetProfit = remember(displayData) { displayData.sumOf { it.netProfit } }

    AppBackground {
        Scaffold(
            topBar = {
                BackTopAppBar(
                    title = {
                        Text(
                            text = "Monthly Finances",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    onBack = onBack
                )
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isLoading) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 6,
                        cardShaped = true,
                        spacing = Spacing.md,
                        contentPadding = PaddingValues(Spacing.screen)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.screen,
                            end = Spacing.screen,
                            top = Spacing.sm,
                            bottom = Spacing.xxl
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        item {
                            // Filter row for Overall and Financial Years
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                filterOptions.forEach { option ->
                                    val isSelected = selectedFilter == option
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedFilter = option },
                                        label = {
                                            Text(
                                                text = option,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        item {
                            // Hero Summary Card
                            MonthlyFinancesHero(
                                filter = selectedFilter,
                                revenue = totalPeriodRevenue,
                                expenses = totalPeriodExpenses,
                                netProfit = totalPeriodNetProfit,
                                activeMonthsCount = displayData.size
                            )
                        }

                        if (displayData.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.xxl),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = "No financial data for $selectedFilter",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "No revenue or expense records recorded for this period.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(displayData, key = { it.key }) { item ->
                                MonthFinanceCard(
                                    item = item,
                                    onClick = { onMonthClick(item.key) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyFinancesHero(
    filter: String,
    revenue: Double,
    expenses: Double,
    netProfit: Double,
    activeMonthsCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$filter Summary",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$activeMonthsCount month${if (activeMonthsCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = StatisticsUtils.formatRupees(netProfit),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (netProfit >= 0) {
                    LocalCustomColors.current.textSuccess
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Text(
                text = "Net profit for selected period",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TOTAL REVENUE",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = StatisticsUtils.formatRupees(revenue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "TOTAL EXPENSES",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = StatisticsUtils.formatRupees(expenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalCustomColors.current.textPink
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthFinanceCard(
    item: FinanceSummaryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val customColors = LocalCustomColors.current

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(Spacing.card)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = if (item.netProfit >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (item.netProfit >= 0) customColors.textSuccess else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (item.isProfitComplete) {
                                "Net: ${StatisticsUtils.formatRupees(item.netProfit)}"
                            } else {
                                "Net: Unavailable (missing cost)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.netProfit >= 0) customColors.textSuccess else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View ${item.label} details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                FinanceMiniCardMetric(
                    label = "Revenue",
                    value = StatisticsUtils.formatRupees(item.revenue),
                    accentColor = customColors.textBlue,
                    modifier = Modifier.weight(1f)
                )
                FinanceMiniCardMetric(
                    label = "COGS",
                    value = if (item.isProfitComplete) StatisticsUtils.formatRupees(item.vaccineCost) else "—",
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                FinanceMiniCardMetric(
                    label = "Expense",
                    value = StatisticsUtils.formatRupees(item.expenses),
                    accentColor = customColors.textPink,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FinanceMiniCardMetric(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
