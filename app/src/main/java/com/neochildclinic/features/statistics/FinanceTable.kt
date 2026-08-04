package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Vaccine
import java.util.*

@Composable
fun FinanceTable(vaccinations: List<Vaccination>, inventory: List<Vaccine>, onMonthClick: (String) -> Unit = {}) {
    val displayData = FinanceCalculator.getMonthlyGroupedData(vaccinations, inventory)

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        FinanceTableHeader()
        HorizontalDivider()

        displayData.reversed().forEachIndexed { index, data ->
            val prevData = if (index + 1 < displayData.size) displayData[displayData.size - 2 - index] else null
            FinanceTableRow(data, prevData, onMonthClick)
            if (index < displayData.size - 1) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        FinanceTableTotalRow(displayData)
    }
}

@Composable
private fun FinanceTableHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Month", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text("Revenue", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        Text("Profit", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
        Text("Imp. %", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
    }
}

@Composable
private fun FinanceTableRow(data: FinanceSummaryItem, prevData: FinanceSummaryItem?, onMonthClick: (String) -> Unit) {
    val improvement = FinanceCalculator.calculateImprovement(data.profit, prevData?.profit ?: 0.0)

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onMonthClick(data.key) }.padding(vertical = 12.dp), 
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(data.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text("₹${data.revenue.toInt()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Text("₹${data.profit.toInt()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = if (data.profit > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
        Text(
            text = if (improvement == 0.0) "-" else String.format(Locale.getDefault(), "%.1f%%", improvement),
            modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
            color = if (improvement >= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun FinanceTableTotalRow(dataList: List<FinanceSummaryItem>) {
    val totalRevenue = dataList.sumOf { it.revenue }
    val totalProfit = dataList.sumOf { it.profit }
    
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("TOTAL", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Text("₹${totalRevenue.toInt()}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        Text("₹${totalProfit.toInt()}", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, color = if (totalProfit > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
        Text("-", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
    }
}
