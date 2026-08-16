package com.neochildclinic.features.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.core.utils.PatientUtils
import java.util.*

@Composable
fun ChartsSection(filterMode: String, availableYears: List<String>, transactions: List<FinanceEntity>, vaccinations: List<com.neochildclinic.domain.model.Vaccination>) {
    if (filterMode == "Overall") {
        YearlyTrendCharts(availableYears = availableYears, transactions = transactions, vaccinations = vaccinations)
    } else {
        MonthlyTrendCharts(filterMode = filterMode, transactions = transactions, vaccinations = vaccinations)
    }
}

@Composable
fun YearlyTrendCharts(availableYears: List<String>, transactions: List<FinanceEntity>, vaccinations: List<com.neochildclinic.domain.model.Vaccination>) {
    Text("Total Revenue Trend (Yearly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    val yearly = remember(availableYears, transactions, vaccinations) {
        availableYears.map { fy ->
            FinanceCalculator.calculateFinanceStats(
                transactions.filter { StatisticsUtils.isDateInFilter(it.timestamp, "FY $fy") },
                vaccinations,
                transactions
            )
        }
    }
    MultiBarChart(availableYears, yearly.map { listOf(it.totalRevenue.toFloat()) }, colors = listOf(MaterialTheme.colorScheme.primary))

    Spacer(modifier = Modifier.height(32.dp))
    Text("Cash vs Online Breakdown (Yearly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    MultiBarChart(availableYears, yearly.map { listOf(it.cashTotal.toFloat(), it.onlineTotal.toFloat()) }, colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3)))
}

@Composable
fun MonthlyTrendCharts(filterMode: String, transactions: List<FinanceEntity>, vaccinations: List<com.neochildclinic.domain.model.Vaccination>) {
    val monthLabels = listOf("Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar")
    val startYearShort = filterMode.substringAfter("FY ").substringBefore("-").toIntOrNull() ?: return
    val fyStartYear = if (startYearShort > 80) 1900 + startYearShort else 2000 + startYearShort
    val monthStats = remember(filterMode, transactions, vaccinations) {
        (0 until 12).map { offset ->
            val monthIdx = (Calendar.APRIL + offset) % 12
            val year = if (monthIdx >= Calendar.APRIL) fyStartYear else fyStartYear + 1
            val monthTx = transactions.filter { tx ->
                val date = PatientUtils.parseDate(tx.timestamp) ?: return@filter false
                val cal = Calendar.getInstance().apply { time = date }
                cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == monthIdx
            }
            FinanceCalculator.calculateFinanceStats(monthTx, vaccinations, transactions)
        }
    }

    Text("Total Revenue Trend (Monthly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    MultiBarChart(monthLabels, monthStats.map { listOf(it.totalRevenue.toFloat()) }, colors = listOf(MaterialTheme.colorScheme.primary))

    Spacer(modifier = Modifier.height(32.dp))
    Text("Cash vs Online Breakdown (Monthly)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    MultiBarChart(monthLabels, monthStats.map { listOf(it.cashTotal.toFloat(), it.onlineTotal.toFloat()) }, colors = listOf(Color(0xFF4CAF50), Color(0xFF2196F3)))
}

@Composable
fun ChartLegend() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        LegendItem("Cash", Color(0xFF4CAF50))
        Spacer(modifier = Modifier.width(16.dp))
        LegendItem("Online", Color(0xFF2196F3))
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(" $label", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MultiBarChart(labels: List<String>, dataSets: List<List<Float>>, colors: List<Color> = listOf(MaterialTheme.colorScheme.primary)) {
    val maxVal = remember(dataSets) { dataSets.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1f }
    
    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                dataSets.forEach { values ->
                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        values.forEachIndexed { idx, v ->
                            val barWidth = if (values.size == 1) 12.dp else 16.dp
                            Box(
                                modifier = Modifier
                                    .width(barWidth)
                                    .fillMaxHeight((v / maxVal).coerceIn(0.01f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(colors[idx % colors.size].copy(alpha = 0.6f))
                            )
                            if (idx < values.size - 1) Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                labels.forEach { label ->
                    Text(text = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 4.dp)) {
            val width = size.width
            val height = size.height
            val itemWidth = width / dataSets.size
            
            for (setIdx in 0 until dataSets[0].size) {
                val path = Path()
                var first = true
                dataSets.forEachIndexed { i, values ->
                    if (setIdx < values.size) {
                        val x = i * itemWidth + (itemWidth / 2)
                        val y = height - (values[setIdx] / maxVal) * height
                        if (first) { path.moveTo(x, y); first = false } else { path.lineTo(x, y) }
                        drawCircle(color = colors[setIdx % colors.size], radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                }
                drawPath(path = path, color = colors[setIdx % colors.size], style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
