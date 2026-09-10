package com.neochildclinic.features.statistics

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
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
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
            compareByDescending<FinanceEntity> { FinanceCalculator.resolveReportingDate(it) }
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
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                "Net profit is unavailable because ${monthStats.missingCogsSnapshotCount} vaccination income transaction(s) are missing historical COGS data.",
                                modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier.fillMaxWidth().padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No financial transactions found for this month",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
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

@Composable
private fun FinanceMonthSummary(
    revenue: Double,
    cash: Double,
    online: Double,
    cogsAndExpenses: Double,
    netProfit: Double,
    profitAvailable: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryAmountCard(
            title = "Revenue",
            amount = revenue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaymentSummary("Cash", cash, Modifier.weight(1f))
                PaymentSummary("Online", online, Modifier.weight(1f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryAmountCard(
                title = "COGS + Expenses",
                amount = cogsAndExpenses,
                modifier = Modifier.weight(1f)
            )
            SummaryAmountCard(
                title = "Net Profit",
                amount = netProfit,
                modifier = Modifier.weight(1f),
                valueAvailable = profitAvailable
            )
        }
    }
}

@Composable
private fun SummaryAmountCard(
    title: String,
    amount: Double,
    modifier: Modifier,
    valueAvailable: Boolean = true,
    content: @Composable (() -> Unit)? = null
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (valueAvailable) formatCurrency(amount) else "Unavailable",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            content?.invoke()
        }
    }
}

@Composable
private fun PaymentSummary(label: String, amount: Double, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatCurrency(amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FinanceTransactionCard(
    transaction: FinanceEntity,
    vaccination: Vaccination?,
    patient: Patient?
) {
    val isIncome = transaction.type.equals("INCOME", ignoreCase = true)
    val accent = MaterialTheme.colorScheme.primary
    val category = transaction.category.replace('_', ' ').lowercase(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
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

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "${if (isIncome) "INCOME" else "EXPENSE"} • $category",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    patientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Given date: ${PatientUtils.formatDateForDisplay(date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    paymentModeLabel(transaction),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun paymentModeLabel(transaction: FinanceEntity): String {
    return when {
        transaction.paymentMethod.equals("MIXED", true) ||
            (transaction.cashAmount > 0 && transaction.onlineAmount > 0) -> "Cash + Online"
        transaction.paymentMethod.equals("CASH", true) || transaction.cashAmount > 0 -> "Cash"
        transaction.paymentMethod.equals("ONLINE", true) || transaction.onlineAmount > 0 -> "Online"
        transaction.paymentMethod.isNotBlank() -> transaction.paymentMethod.replace('_', ' ').lowercase(Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        else -> "—"
    }
}

private fun formatCurrency(amount: Double): String =
    "₹" + String.format(Locale.US, "%,.0f", amount.coerceAtLeast(0.0))

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
