package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.statistics.domain.FinanceCalculator
import com.neochildclinic.feature.statistics.domain.FinanceSummaryItem
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import java.util.Locale

@Composable
fun FinanceTable(
    transactions: List<FinanceTransaction>,
    vaccinations: List<Vaccination>,
    filterMode: String,
    fyQuarter: Int = 0,
    selectedMonth: Int = -1,
    onMonthClick: (String) -> Unit = {},
    visitDatesById: Map<String, String>? = null,
    expenses: List<Expense> = emptyList()
) {
    val displayData = remember(transactions, vaccinations, filterMode, fyQuarter, selectedMonth, visitDatesById, expenses) {
        FinanceCalculator.getMonthlyGroupedData(
            transactions = transactions,
            vaccinations = vaccinations,
            filterMode = filterMode,
            selectedQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            visitDatesById = visitDatesById,
            expenses = expenses
        )
    }

    if (displayData.isEmpty()) {
        StatisticsEmptyState(
            icon = Icons.Default.ReceiptLong,
            title = "No monthly data",
            message = "No financial records match the selected period."
        )
        return
    }

    val reversed = displayData.asReversed()
    val customColors = LocalCustomColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StatTileShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
        )
    ) {
        Column {
            reversed.forEachIndexed { index, data ->
                val previous = reversed.getOrNull(index + 1)
                FinanceMonthRow(
                    data = data,
                    previous = previous,
                    onMonthClick = onMonthClick
                )
                if (index < reversed.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.card),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )
            FinanceTotalRow(
                dataList = displayData,
                positiveColor = customColors.textSuccess
            )
        }
    }
}

@Composable
private fun FinanceMonthRow(
    data: FinanceSummaryItem,
    previous: FinanceSummaryItem?,
    onMonthClick: (String) -> Unit
) {
    val customColors = LocalCustomColors.current
    val improvement = if (data.isProfitComplete && (previous?.isProfitComplete != false)) {
        FinanceCalculator.calculateImprovement(data.netProfit, previous?.netProfit ?: 0.0)
    } else {
        null
    }
    val netColor = when {
        !data.isProfitComplete -> MaterialTheme.colorScheme.onSurfaceVariant
        data.netProfit >= 0 -> customColors.textSuccess
        else -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMonthClick(data.key) }
            .padding(Spacing.card)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (improvement != null) {
                        "${improvementLabel(improvement)} vs previous month"
                    } else {
                        "No comparison available"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        improvement == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        improvement >= 0 -> customColors.textSuccess
                        else -> MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (data.isProfitComplete) StatisticsUtils.formatRupees(data.netProfit) else "Unavailable",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = netColor
                )
                Text(
                    text = "net",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FinanceMiniMetric(
                label = "Revenue",
                value = StatisticsUtils.formatRupees(data.revenue),
                modifier = Modifier.weight(1f)
            )
            FinanceMiniMetric(
                label = "COGS",
                value = if (data.isProfitComplete) StatisticsUtils.formatRupees(data.vaccineCost) else "Unavailable",
                modifier = Modifier.weight(1f)
            )
            FinanceMiniMetric(
                label = "Expense",
                value = StatisticsUtils.formatRupees(data.expenses),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FinanceMiniMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FinanceTotalRow(dataList: List<FinanceSummaryItem>, positiveColor: androidx.compose.ui.graphics.Color) {
    val totalRevenue = dataList.sumOf { it.revenue }
    val totalCogs = dataList.sumOf { it.vaccineCost }
    val totalExpenses = dataList.sumOf { it.expenses }
    val allProfitComplete = dataList.all { it.isProfitComplete }
    val totalNet = dataList.sumOf { it.netProfit }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Spacing.card)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (allProfitComplete) StatisticsUtils.formatRupees(totalNet) else "Unavailable",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    !allProfitComplete -> MaterialTheme.colorScheme.onSurfaceVariant
                    totalNet >= 0 -> positiveColor
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            FinanceMiniMetric(
                label = "Revenue",
                value = StatisticsUtils.formatRupees(totalRevenue),
                modifier = Modifier.weight(1f)
            )
            FinanceMiniMetric(
                label = "COGS",
                value = if (allProfitComplete) StatisticsUtils.formatRupees(totalCogs) else "Unavailable",
                modifier = Modifier.weight(1f)
            )
            FinanceMiniMetric(
                label = "Expense",
                value = StatisticsUtils.formatRupees(totalExpenses),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun improvementLabel(value: Double): String =
    String.format(Locale.getDefault(), "%+.1f%%", value)
