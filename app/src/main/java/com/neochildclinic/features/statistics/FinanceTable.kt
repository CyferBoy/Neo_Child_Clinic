package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.Vaccination
import java.util.Locale

@Composable
fun FinanceTable(
    transactions: List<FinanceEntity>,
    vaccinations: List<Vaccination>,
    filterMode: String,
    fyQuarter: Int = 0,
    selectedMonth: Int = -1,
    onMonthClick: (String) -> Unit = {}
) {
    val displayData = FinanceCalculator.getMonthlyGroupedData(
        transactions = transactions,
        vaccinations = vaccinations,
        filterMode = filterMode,
        selectedQuarter = fyQuarter,
        selectedMonth = selectedMonth
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        FinanceTableHeader()
        HorizontalDivider()
        displayData.asReversed().forEachIndexed { index, data ->
            val previous = displayData.asReversed().getOrNull(index + 1)
            FinanceTableRow(data, previous, onMonthClick)
            if (index < displayData.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
        FinanceTableTotalRow(displayData)
    }
}

@Composable
private fun FinanceTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Month", Modifier.weight(1.15f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Revenue", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        Text("COGS", Modifier.weight(0.9f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        Text("Expense", Modifier.weight(0.95f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        Text("Net", Modifier.weight(0.95f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
    }
}

@Composable
private fun FinanceTableRow(data: FinanceSummaryItem, previous: FinanceSummaryItem?, onMonthClick: (String) -> Unit) {
    val improvement = if (data.isProfitComplete && (previous?.isProfitComplete != false)) FinanceCalculator.calculateImprovement(data.netProfit, previous?.netProfit ?: 0.0) else null
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onMonthClick(data.key) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(data.label, Modifier.weight(1.15f), style = MaterialTheme.typography.bodySmall)
        Text("₹${data.revenue.toInt()}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Text(if (data.isProfitComplete) "₹${data.vaccineCost.toInt()}" else "Unavailable", Modifier.weight(0.9f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Text("₹${data.expenses.toInt()}", Modifier.weight(0.95f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Column(Modifier.weight(0.95f), horizontalAlignment = Alignment.End) {
            Text(
                if (data.isProfitComplete) "₹${data.netProfit.toInt()}" else "Unavailable",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.End,
                color = if (data.netProfit >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
            Text(
                improvement?.let { String.format(Locale.getDefault(), "%+.1f%%", it) } ?: "N/A",
                style = MaterialTheme.typography.labelSmall,
                color = if ((improvement ?: 0.0) >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun FinanceTableTotalRow(dataList: List<FinanceSummaryItem>) {
    val totalRevenue = dataList.sumOf { it.revenue }
    val totalCogs = dataList.sumOf { it.vaccineCost }
    val totalExpenses = dataList.sumOf { it.expenses }
    val allProfitComplete = dataList.all { it.isProfitComplete }
    val totalNet = dataList.sumOf { it.netProfit }
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("TOTAL", Modifier.weight(1.15f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Text("₹${totalRevenue.toInt()}", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Text(if (allProfitComplete) "₹${totalCogs.toInt()}" else "Unavailable", Modifier.weight(0.9f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Text("₹${totalExpenses.toInt()}", Modifier.weight(0.95f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Text(if (allProfitComplete) "₹${totalNet.toInt()}" else "Unavailable", Modifier.weight(0.95f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
    }
}
