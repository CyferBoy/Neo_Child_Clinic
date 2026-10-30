package com.neochildclinic.feature.statistics.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.statistics.domain.FinanceCalculator
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyFinanceDetailsScreen(
    monthKey: String,
    onBack: () -> Unit,
    onViewAllExpenses: () -> Unit = {},
    viewModel: FinanceDetailsViewModel = hiltViewModel()
) {
    val allTransactions by viewModel.transactions.collectAsState()
    val allVaccinations by viewModel.vaccinations.collectAsState()
    val allExpenses by viewModel.expenses.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showTransactions by rememberSaveable { mutableStateOf(false) }

    // Parse monthKey (e.g., "2026-10" for October 2026)
    val year = remember(monthKey) { monthKey.substringBefore("-").toIntOrNull() ?: 2026 }
    val month1Based = remember(monthKey) { monthKey.substringAfter("-").toIntOrNull() ?: 1 }
    val monthIndex = (month1Based - 1).coerceIn(0, 11)

    val monthNames = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    val title = remember(year, monthIndex) {
        "${monthNames.getOrElse(monthIndex) { "Month" }} $year"
    }

    val (prevYear, prevMonth1Based) = remember(year, month1Based) {
        if (month1Based <= 1) (year - 1) to 12 else year to (month1Based - 1)
    }
    val prevMonthKey = remember(prevYear, prevMonth1Based) {
        String.format(Locale.US, "%04d-%02d", prevYear, prevMonth1Based)
    }
    val prevMonthName = remember(prevMonth1Based) { monthNames[(prevMonth1Based - 1).coerceIn(0, 11)] }

    // 1. Filter valid vaccinations (strictly excludes cancelled / dismissed / soft-deleted visits)
    val validVaccinations = remember(allVaccinations) {
        StatisticsUtils.filterValidVaccinations(allVaccinations)
    }
    val validVaccinationIds = remember(validVaccinations) {
        validVaccinations.map { it.id }.toSet()
    }
    val vaccinationById = remember(validVaccinations) {
        validVaccinations.associateBy { it.id }
    }

    // 2. Active patients
    val validPatients = remember(patients) {
        patients.filter { it.name.isNotBlank() }
    }
    val patientById = remember(validPatients) {
        validPatients.associateBy { it.id }
    }

    // 3. Transactions: strictly filter out deleted items
    val cleanTransactions = remember(allTransactions, validVaccinationIds, patientById) {
        allTransactions.filter { tx ->
            if (tx.amount <= 0) return@filter false
            if (tx.remarks?.contains("[DELETED]", ignoreCase = true) == true) return@filter false
            if (tx.remarks?.contains("is_deleted", ignoreCase = true) == true) return@filter false
            // If linked to a patient, patient must exist (not deleted)
            if (!tx.patientId.isNullOrBlank() && !patientById.containsKey(tx.patientId)) {
                return@filter false
            }
            // If vaccination income, visit must exist in valid vaccinations (not deleted/cancelled)
            if (tx.category.equals("VACCINATION", ignoreCase = true) && !tx.visitId.isNullOrBlank()) {
                if (tx.visitId !in validVaccinationIds) {
                    return@filter false
                }
            }
            true
        }
    }

    // 4. Expenses: strictly filter out deleted items
    val cleanExpenses = remember(allExpenses) {
        allExpenses.filter { exp ->
            if (exp.amountPaise <= 0) return@filter false
            if (exp.title.contains("DELETED", ignoreCase = true)) return@filter false
            if (exp.description?.contains("DELETED", ignoreCase = true) == true) return@filter false
            true
        }
    }

    // Filter for current month
    val monthTransactions = remember(cleanTransactions, monthKey) {
        cleanTransactions.filter { tx ->
            val date = PatientUtils.parseDate(FinanceCalculator.resolveReportingDate(tx))
                ?: return@filter false
            val cal = Calendar.getInstance().apply { time = date }
            val key = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            key == monthKey
        }.sortedWith(
            compareByDescending<FinanceTransaction> { FinanceCalculator.resolveReportingDate(it) }
                .thenByDescending { it.timestamp }
        )
    }

    val monthExpenseItems = remember(cleanExpenses, monthKey) {
        cleanExpenses.filter { exp ->
            val date = PatientUtils.parseDate(exp.expenseDate) ?: return@filter false
            val cal = Calendar.getInstance().apply { time = date }
            val key = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            key == monthKey
        }
    }

    // Income calculations
    val monthIncome = remember(monthTransactions) {
        monthTransactions.filter { it.type.equals("INCOME", ignoreCase = true) }
    }
    val effectiveVaccinationIncome = remember(monthIncome) {
        FinanceCalculator.deduplicateVaccinationIncome(monthIncome)
    }
    val effectiveIncome = remember(monthIncome, effectiveVaccinationIncome) {
        monthIncome.filterNot { it.category.equals("VACCINATION", ignoreCase = true) } + effectiveVaccinationIncome
    }
    val totalIncome = remember(effectiveIncome) {
        effectiveIncome.sumOf { it.amount.coerceAtLeast(0.0) }
    }

    val vaccinationsIncome = remember(effectiveVaccinationIncome) {
        effectiveVaccinationIncome.sumOf { it.amount.coerceAtLeast(0.0) }
    }
    val consultationsIncome = remember(effectiveIncome) {
        effectiveIncome.filter { it.category.equals("CONSULTATION", ignoreCase = true) }
            .sumOf { it.amount.coerceAtLeast(0.0) }
    }
    val otherIncome = remember(effectiveIncome) {
        effectiveIncome.filterNot {
            it.category.equals("VACCINATION", ignoreCase = true) ||
                it.category.equals("CONSULTATION", ignoreCase = true)
        }.sumOf { it.amount.coerceAtLeast(0.0) }
    }

    val cashIncome = remember(effectiveIncome) {
        FinanceCalculator.paymentAmount(effectiveIncome, true)
    }
    val onlineIncome = remember(effectiveIncome) {
        FinanceCalculator.paymentAmount(effectiveIncome, false)
    }
    val cashPercent = remember(totalIncome, cashIncome) {
        if (totalIncome > 0) (cashIncome / totalIncome * 100).roundToInt() else 0
    }
    val onlinePercent = remember(totalIncome, onlineIncome, cashPercent) {
        if (totalIncome > 0) (100 - cashPercent).coerceAtLeast(0) else 0
    }

    // Expenses calculation
    val expenseTransactions = remember(monthTransactions) {
        monthTransactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }
    }
    val totalExpenses = remember(expenseTransactions, monthExpenseItems) {
        expenseTransactions.sumOf { it.amount.coerceAtLeast(0.0) } +
            monthExpenseItems.sumOf { it.amountRupees }
    }

    val expensesByCategory = remember(monthExpenseItems, expenseTransactions) {
        val map = mutableMapOf<String, Double>()
        monthExpenseItems.forEach { exp ->
            val cat = exp.category.label
            map[cat] = (map[cat] ?: 0.0) + exp.amountRupees
        }
        expenseTransactions.forEach { tx ->
            val cat = categoryLabel(tx.category)
            map[cat] = (map[cat] ?: 0.0) + tx.amount.coerceAtLeast(0.0)
        }
        map.toList().sortedByDescending { it.second }
    }

    // Net Profit
    val netProfit = remember(totalIncome, totalExpenses) {
        totalIncome - totalExpenses
    }
    val marginPercent = remember(totalIncome, netProfit) {
        if (totalIncome > 0) (netProfit / totalIncome * 100) else 0.0
    }

    // Previous month comparison
    val prevMonthTransactions = remember(cleanTransactions, prevMonthKey) {
        cleanTransactions.filter { tx ->
            val date = PatientUtils.parseDate(FinanceCalculator.resolveReportingDate(tx))
                ?: return@filter false
            val cal = Calendar.getInstance().apply { time = date }
            val key = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            key == prevMonthKey
        }
    }
    val prevMonthExpenseItems = remember(cleanExpenses, prevMonthKey) {
        cleanExpenses.filter { exp ->
            val date = PatientUtils.parseDate(exp.expenseDate) ?: return@filter false
            val cal = Calendar.getInstance().apply { time = date }
            val key = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            key == prevMonthKey
        }
    }
    val prevIncome = remember(prevMonthTransactions) {
        val inc = prevMonthTransactions.filter { it.type.equals("INCOME", ignoreCase = true) }
        val effVacc = FinanceCalculator.deduplicateVaccinationIncome(inc)
        val effInc = inc.filterNot { it.category.equals("VACCINATION", ignoreCase = true) } + effVacc
        effInc.sumOf { it.amount.coerceAtLeast(0.0) }
    }
    val prevExpenses = remember(prevMonthTransactions, prevMonthExpenseItems) {
        prevMonthTransactions.filter { it.type.equals("EXPENSE", ignoreCase = true) }.sumOf { it.amount.coerceAtLeast(0.0) } +
            prevMonthExpenseItems.sumOf { it.amountRupees }
    }
    val prevNetProfit = remember(prevIncome, prevExpenses) {
        prevIncome - prevExpenses
    }
    val hasPrevData = remember(prevIncome, prevExpenses) {
        prevIncome > 0 || prevExpenses > 0
    }

    val customColors = LocalCustomColors.current
    val context = LocalContext.current

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            android.widget.Toast.makeText(
                                context,
                                "Exporting monthly statement for $title...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export statement",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                    ) {
                        // Subtitle
                        Text(
                            text = "Monthly finance details",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = customColors.muted
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Income & Expense Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Income Card
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(88.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "INCOME",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.04.sp
                                        ),
                                        color = customColors.muted
                                    )
                                    Text(
                                        text = StatisticsUtils.formatRupees(totalIncome),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp
                                        ),
                                        color = customColors.textSuccess,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Expense Card
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(88.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "EXPENSE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.04.sp
                                        ),
                                        color = customColors.muted
                                    )
                                    Text(
                                        text = StatisticsUtils.formatRupees(totalExpenses),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp
                                        ),
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Net Profit Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Net profit",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = StatisticsUtils.formatRupees(netProfit),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val netProfitSubtitle = if (hasPrevData) {
                                    String.format(
                                        Locale.US,
                                        "%.1f%% margin · vs %s in %s",
                                        marginPercent,
                                        StatisticsUtils.formatRupees(prevNetProfit),
                                        prevMonthName
                                    )
                                } else {
                                    String.format(Locale.US, "%.1f%% margin", marginPercent)
                                }
                                Text(
                                    text = netProfitSubtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Income Section
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            ) {
                                Text(
                                    text = "BY SERVICE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.08.sp
                                    ),
                                    color = customColors.muted
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                // Vaccinations
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Vaccinations",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = StatisticsUtils.formatRupees(vaccinationsIncome),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Consultations
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Consultations",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = StatisticsUtils.formatRupees(consultationsIncome),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                if (otherIncome > 0) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.tertiary)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Other Services",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = StatisticsUtils.formatRupees(otherIncome),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    thickness = 1.dp
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "BY PAYMENT METHOD",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.08.sp
                                    ),
                                    color = customColors.muted
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Cash ${StatisticsUtils.formatRupees(cashIncome)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(24.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF6941C6))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Online ${StatisticsUtils.formatRupees(onlineIncome)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Cash $cashPercent% · Online $onlinePercent% of total income",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp
                                    ),
                                    color = customColors.muted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Expenses Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Expenses",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "View all",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(onClick = onViewAllExpenses)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                if (expensesByCategory.isEmpty()) {
                                    Text(
                                        text = "No expenses recorded this month",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 12.sp
                                        ),
                                        color = customColors.muted,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    expensesByCategory.take(6).forEachIndexed { index, (category, amount) ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                thickness = 1.dp,
                                                modifier = Modifier.padding(vertical = 10.dp)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = category,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 12.sp
                                                ),
                                                color = customColors.muted
                                            )
                                            Text(
                                                text = StatisticsUtils.formatRupees(amount),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Recent Transactions Section
                        Text(
                            text = "Recent transactions",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showTransactions = !showTransactions },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (showTransactions) "Hide transactions" else "View ${monthTransactions.size} transactions",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        AnimatedVisibility(visible = showTransactions) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (monthTransactions.isEmpty()) {
                                    StatisticsEmptyState(
                                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                        title = "No transactions",
                                        message = "No transactions found for this month."
                                    )
                                } else {
                                    monthTransactions.forEach { transaction ->
                                        FinanceTransactionCard(
                                            transaction = transaction,
                                            vaccination = transaction.visitId?.let { vaccinationById[it] },
                                            patient = transaction.patientId?.let { patientById[it] }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Footer Note
                        Text(
                            text = "Figures are based on local records only.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = customColors.muted
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
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
    val amountText = if (isIncome) StatisticsUtils.formatRupees(transaction.amount) else "-${StatisticsUtils.formatRupees(transaction.amount)}"
    val date = FinanceCalculator.resolveReportingDate(transaction)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.card),
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
                    color = if (isIncome) customColors.textSuccess else MaterialTheme.colorScheme.error,
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

@Preview(showBackground = true)
@Composable
private fun MonthlyFinancePreview() {
    NeoChildTheme {
        MonthlyFinanceDetailsScreen(
            monthKey = "2026-10",
            onBack = {}
        )
    }
}
