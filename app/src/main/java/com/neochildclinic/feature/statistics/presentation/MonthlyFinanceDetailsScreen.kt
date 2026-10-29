package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.statistics.domain.FinanceCalculator
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyFinanceDetailsScreen(
    monthKey: String,
    onBack: () -> Unit,
    viewModel: FinanceDetailsViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.transactions.collectAsState()
    val allVaccinations by viewModel.vaccinations.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val filtered = remember(allTransactions, monthKey) {
        allTransactions.filter { transaction ->
            val date = PatientUtils.parseDate(FinanceCalculator.resolveReportingDate(transaction))
                ?: return@filter false
            val cal = Calendar.getInstance().apply { time = date }
            val key = String.format(
                Locale.US,
                "%04d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH)
            )
            key == monthKey
        }.sortedWith(
            compareByDescending<FinanceTransaction> { FinanceCalculator.resolveReportingDate(it) }
                .thenByDescending { it.timestamp }
        )
    }

    val title = remember(monthKey) {
        val names = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        val month = monthKey.substringAfter("-").toInt()
        "${names[month]} ${monthKey.substringBefore("-")}"
    }

    val monthVaccinations = remember(allVaccinations, monthKey) {
        StatisticsUtils.filterValidVaccinations(allVaccinations).filter { vaccination ->
            val date = PatientUtils.parseDate(vaccination.dateGiven) ?: return@filter false
            val cal = Calendar.getInstance().apply { time = date }
            val key = String.format(
                Locale.US,
                "%04d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH)
            )
            key == monthKey
        }
    }

    val monthStats = remember(filtered, allVaccinations, monthVaccinations, allTransactions) {
        FinanceCalculator.calculateFinanceStats(
            filtered,
            allVaccinations,
            allTransactions,
            monthVaccinations
        )
    }

    val vaccinationById = remember(allVaccinations) { allVaccinations.associateBy { it.id } }
    val patientById = remember(patients) { patients.associateBy { it.id } }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    onBack = onBack,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                if (isLoading) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 5,
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
                            top = Spacing.lg,
                            bottom = Spacing.xxl
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        item {
                            FinanceMonthSummary(
                                revenue = monthStats.totalRevenue,
                                cash = monthStats.cashTotal,
                                online = monthStats.onlineTotal,
                                cogsAndExpenses = monthStats.vaccineCost + monthStats.totalExpenses,
                                netProfit = monthStats.netProfit,
                                profitAvailable = monthStats.isProfitComplete
                            )
                        }

                        if (!monthStats.isProfitComplete) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "Net profit is unavailable because ${monthStats.missingCogsSnapshotCount} vaccination income transaction(s) are missing historical COGS data.",
                                        modifier = Modifier.padding(Spacing.cardDense),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        if (filtered.isEmpty()) {
                            item {
                                StatisticsEmptyState(
                                    icon = Icons.Default.ReceiptLong,
                                    title = "No transactions",
                                    message = "No financial transactions were found for this month."
                                )
                            }
                        } else {
                            item {
                                StatisticsSectionHeader(
                                    title = "Transactions",
                                    subtitle = "${filtered.size} entries"
                                )
                            }
                            items(filtered, key = { it.id }) { transaction ->
                                FinanceTransactionCard(
                                    transaction = transaction,
                                    vaccination = transaction.visitId?.let { vaccinationById[it] },
                                    patient = transaction.patientId?.let { patientById[it] }
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
private fun FinanceMonthSummary(
    revenue: Double,
    cash: Double,
    online: Double,
    cogsAndExpenses: Double,
    netProfit: Double,
    profitAvailable: Boolean
) {
    val customColors = LocalCustomColors.current

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SummaryCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Revenue",
            value = StatisticsUtils.formatRupees(revenue),
            accentColor = MaterialTheme.colorScheme.primary,
            supportingText = "Cash ${StatisticsUtils.formatRupees(cash)}  \u00b7  Online ${StatisticsUtils.formatRupees(online)}"
        )

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "COGS + Expenses",
                value = StatisticsUtils.formatRupees(cogsAndExpenses),
                accentColor = customColors.textPink,
                supportingText = "cost of goods + expenses"
            )
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Net Profit",
                value = if (profitAvailable) StatisticsUtils.formatRupees(netProfit) else "Unavailable",
                accentColor = when {
                    !profitAvailable -> MaterialTheme.colorScheme.onSurfaceVariant
                    netProfit >= 0 -> customColors.textSuccess
                    else -> MaterialTheme.colorScheme.error
                },
                supportingText = if (profitAvailable) "after COGS and expenses" else "missing stock cost history"
            )
        }
    }
}

@Composable
private fun FinanceTransactionCard(
    transaction: FinanceTransaction,
    vaccination: Vaccination?,
    patient: Patient?
) {
    val customColors = LocalCustomColors.current
    val isIncome = transaction.type.equals("INCOME", ignoreCase = true)
    val accent = if (isIncome) customColors.textSuccess else customColors.textPink
    val iconContainer = if (isIncome) customColors.successContainer else customColors.softPink
    val category = categoryLabel(transaction.category)
    val patientName = patient?.name?.takeIf { it.isNotBlank() }
        ?: vaccination?.patientName?.takeIf { it.isNotBlank() }
        ?: transaction.remarks?.substringBefore("[COGS_SNAPSHOT:")?.trim()?.takeIf { it.isNotBlank() }
        ?: if (isIncome) "Income" else "Clinic Expense"
    // For vaccination income, the displayed vaccine details must come from the
    // persisted vaccination_items rows attached to this visit. Do not use the
    // legacy visit-level raw_vaccine_names snapshot as a fallback.
    val vaccinationItemNames = vaccination?.items
        ?.map { it.vaccineName.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        ?: emptyList()

    val description = when {
        transaction.category.equals("VACCINATION", ignoreCase = true) -> {
            if (vaccination != null) vaccinationItemNames.joinToString(", ").ifBlank { "Vaccination" }
            else "Vaccination"
        }
        transaction.category.equals("CONSULTATION", ignoreCase = true) -> "Consultation"
        else -> transaction.remarks?.substringBefore("[COGS_SNAPSHOT:")?.trim()?.takeIf { it.isNotBlank() }
            ?: category
    }
    val amountText = if (isIncome) formatCurrency(transaction.amount) else "-${formatCurrency(transaction.amount)}"
    val date = FinanceCalculator.resolveReportingDate(transaction)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StatTileShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.card),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patientName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${if (isIncome) "Income" else "Expense"} \u00b7 $category",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "  \u00b7  ${PatientUtils.formatDateForDisplay(date)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) customColors.textSuccess else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = paymentModeLabel(transaction),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private fun categoryLabel(raw: String): String =
    raw.replace('_', ' ').lowercase(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

private fun paymentModeLabel(transaction: FinanceTransaction): String {
    return when {
        transaction.paymentMethod.equals("MIXED", true) ||
            (transaction.cashAmount > 0 && transaction.onlineAmount > 0) -> "Cash + Online"
        transaction.paymentMethod.equals("CASH", true) || transaction.cashAmount > 0 -> "Cash"
        transaction.paymentMethod.equals("ONLINE", true) || transaction.onlineAmount > 0 -> "Online"
        transaction.paymentMethod.isNotBlank() -> categoryLabel(transaction.paymentMethod)
        else -> "\u2014"
    }
}

private fun formatCurrency(amount: Double): String = StatisticsUtils.formatRupees(amount.coerceAtLeast(0.0))

@Preview(showBackground = true)
@Composable
private fun MonthlyFinancePreview() {
    NeoChildTheme {
        FinanceMonthSummary(
            revenue = 50000.0,
            cash = 30000.0,
            online = 20000.0,
            cogsAndExpenses = 18000.0,
            netProfit = 32000.0,
            profitAvailable = true
        )
    }
}
