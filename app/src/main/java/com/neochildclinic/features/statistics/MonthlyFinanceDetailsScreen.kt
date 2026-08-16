package com.neochildclinic.features.statistics

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.utils.PatientUtils
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
    val filtered = remember(allTransactions, monthKey) {
        allTransactions.filter { tx ->
            val date = PatientUtils.parseDate(tx.timestamp) ?: return@filter false
            val cal = Calendar.getInstance().apply { time = date }
            val key = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            key == monthKey
        }.sortedByDescending { PatientUtils.parseDate(it.timestamp) }
    }
    val title = remember(monthKey) {
        val names = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        "${names[monthKey.substringAfter("-").toInt()]} ${monthKey.substringBefore("-")}"
    }

    val monthVaccinations = remember(allVaccinations, monthKey) {
        StatisticsUtils.filterValidVaccinations(allVaccinations).filter { vaccination ->
            val date = PatientUtils.parseDate(vaccination.dateGiven) ?: return@filter false
            val cal = Calendar.getInstance().apply { time = date }
            val key = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            key == monthKey
        }
    }
    val monthStats = remember(filtered, allVaccinations, monthVaccinations, allTransactions) {
        FinanceCalculator.calculateFinanceStats(filtered, allVaccinations, allTransactions, monthVaccinations)
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("No financial transactions found for this month") }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        FinanceDetailSummary(
                            revenue = monthStats.totalRevenue,
                            cash = monthStats.cashTotal,
                            online = monthStats.onlineTotal,
                            expenses = monthStats.totalExpenses,
                            cogs = monthStats.vaccineCost,
                            netProfit = monthStats.netProfit,
                            profitAvailable = monthStats.isProfitComplete
                        )
                    }
                    if (!monthStats.isProfitComplete) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Text(
                                    "Profit unavailable: ${monthStats.missingCogsSnapshotCount} vaccination income transaction(s) are missing historical COGS data.",
                                    modifier = Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    item {
                        Card {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Profit Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Vaccine cost (COGS): ₹${monthStats.vaccineCost.toInt()}")
                                Text(if (monthStats.isProfitComplete) "Gross profit: ₹${monthStats.grossProfit.toInt()}" else "Gross profit: Unavailable")
                                Text(if (monthStats.isProfitComplete) "Net profit: ₹${monthStats.netProfit.toInt()}" else "Net profit: Unavailable", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(filtered, key = { it.id }) { FinanceTransactionCard(it) }
                }
            }
        }
    }
}

@Composable
private fun FinanceDetailSummary(revenue: Double, expenses: Double, cogs: Double, cash: Double, online: Double, netProfit: Double, profitAvailable: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailCard("Revenue", revenue, Modifier.weight(1f))
            DetailCard("Net", netProfit, Modifier.weight(1f), profitAvailable)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailCard("Cash", cash, Modifier.weight(1f))
            DetailCard("Online", online, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailCard("Expenses", expenses, Modifier.weight(1f))
            DetailCard("COGS", cogs, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailCard(label: String, amount: Double, modifier: Modifier, valueAvailable: Boolean = true) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(if (valueAvailable) "₹${amount.toInt()}" else "Unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FinanceTransactionCard(transaction: FinanceEntity) {
    val amountText = if (transaction.type == "EXPENSE") "-₹${transaction.amount.toInt()}" else "₹${transaction.amount.toInt()}"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(transaction.category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(amountText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(transaction.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            transaction.remarks?.substringBefore("[COGS_SNAPSHOT:")?.trim()?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(4.dp))
            Text(PatientUtils.formatDateForDisplay(transaction.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (transaction.paymentMethod.isNotBlank()) {
                Text("Payment: ${transaction.paymentMethod}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthlyFinancePreview() {
    NeoChildTheme { FinanceDetailSummary(5000.0, 1000.0, 800.0, 3000.0, 2000.0, 3200.0) }
}
