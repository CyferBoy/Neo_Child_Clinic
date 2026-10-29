package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.SkeletonCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullReportScreen(
    onBack: () -> Unit,
    viewModel: FullReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }
    var chartMode by rememberSaveable { mutableStateOf(ChartMode.BAR) }

    LaunchedEffect(filterMode, fyQuarter, selectedMonth) {
        viewModel.updateFilter(filterMode, fyQuarter, selectedMonth)
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("Full report", fontWeight = FontWeight.Bold) },
                    onBack = onBack,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.lg,
                        bottom = Spacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl)
                ) {
                    item {
                        FilterSection(
                            availableYears = uiState.availableFinancialYears.map { "20$it" },
                            filterMode = filterMode,
                            fyQuarter = fyQuarter,
                            selectedMonth = selectedMonth,
                            onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
                            onQuarterChange = { if (filterMode != "Overall") { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 } },
                            onMonthChange = { if (fyQuarter != 0 && filterMode != "Overall") { selectedMonth = if (selectedMonth == it) -1 else it } }
                        )
                    }

                    item {
                        StatisticsSectionHeader(
                            title = "Charts",
                            subtitle = uiState.periodLabel.ifBlank { "Selected period" }
                        )
                    }

                    if (uiState.errorMessage != null) {
                        item {
                            ErrorBanner(message = uiState.errorMessage ?: "Could not load the report.")
                        }
                    }

                    item {
                        if (uiState.isLoading) {
                            Column {
                                SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 280.dp)
                                Spacer(modifier = Modifier.height(Spacing.lg))
                                SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 280.dp)
                                Spacer(modifier = Modifier.height(Spacing.lg))
                                SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 280.dp)
                            }
                        } else if (uiState.dataPoints.isEmpty()) {
                            StatisticsEmptyState(
                                icon = Icons.Default.BarChart,
                                title = "No data for this period",
                                message = "Change the filters or add clinic records, then refresh."
                            )
                        } else {
                            val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
                            val patientColor = if (darkTheme) ChartPatientsDark else ChartPatients
                            val consultationColor = if (darkTheme) ChartConsultationsDark else ChartConsultations
                            val vaccinationColor = if (darkTheme) ChartVaccinationsDark else ChartVaccinations
                            val revenueColor = if (darkTheme) ChartRevenueDark else ChartRevenue
                            val onlineColor = if (darkTheme) ChartOnlineDark else ChartOnline
                            val cashColor = if (darkTheme) ChartCashDark else ChartCash
                            val netProfitColor = if (darkTheme) ChartNetProfitDark else ChartNetProfit
                            val cogsColor = if (darkTheme) ChartCOGSDark else ChartCOGS
                            val expensesColor = if (darkTheme) ChartExpensesDark else ChartExpenses

                            val labels = uiState.dataPoints.map { it.label }
                            val isLine = chartMode == ChartMode.LINE
                            // FullReportDataPoint.expenses is exposed in rupees while every other
                            // finance series is in ₹K. Normalize for display only; the ViewModel,
                            // calculation and stored values are untouched.
                            val expensesK = uiState.dataPoints.map { it.expenses / 1000f }

                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                                ChartModeToggle(
                                    chartMode = chartMode,
                                    onModeSelected = {
                                        chartMode = it
                                        viewModel.setChartMode(it)
                                    }
                                )

                                BarLineChart(
                                    title = "Patient activity",
                                    subtitle = uiState.periodLabel,
                                    labels = labels,
                                    series = listOf(
                                        BarLineSeries("Patients", uiState.dataPoints.map { it.patients }, patientColor, isLine = isLine),
                                        BarLineSeries("Consultations", uiState.dataPoints.map { it.consultations }, consultationColor, isLine = isLine),
                                        BarLineSeries("Vaccinations", uiState.dataPoints.map { it.vaccinations }, vaccinationColor, isLine = isLine)
                                    ),
                                    yLabel = ""
                                )

                                BarLineChart(
                                    title = "Financial overview",
                                    subtitle = "Revenue, payments and profit (\u20b9K)",
                                    labels = labels,
                                    series = listOf(
                                        BarLineSeries("Revenue", uiState.dataPoints.map { it.revenue }, revenueColor, isLine = isLine),
                                        BarLineSeries("Online", uiState.dataPoints.map { it.online }, onlineColor, isLine = isLine),
                                        BarLineSeries("Cash", uiState.dataPoints.map { it.cash }, cashColor, isLine = isLine),
                                        BarLineSeries("Net Profit", uiState.dataPoints.map { it.netProfit }, netProfitColor, isLine = isLine)
                                    ),
                                    yLabel = "\u20b9K"
                                )

                                BarLineChart(
                                    title = "Financial breakdown",
                                    subtitle = "Revenue, COGS, expenses and profit (\u20b9K)",
                                    labels = labels,
                                    series = listOf(
                                        BarLineSeries("Revenue", uiState.dataPoints.map { it.revenue }, revenueColor, isLine = isLine),
                                        BarLineSeries("COGS", uiState.dataPoints.map { it.cogs }, cogsColor, isLine = isLine),
                                        BarLineSeries("Expenses", expensesK, expensesColor, isLine = isLine),
                                        BarLineSeries("Net Profit", uiState.dataPoints.map { it.netProfit }, netProfitColor, isLine = isLine)
                                    ),
                                    yLabel = "\u20b9K"
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
private fun ErrorBanner(message: String) {
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
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChartModeToggle(
    chartMode: ChartMode,
    onModeSelected: (ChartMode) -> Unit
) {
    val options = listOf("Bar" to ChartMode.BAR, "Line" to ChartMode.LINE)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, mode) ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                onClick = { onModeSelected(mode) },
                selected = chartMode == mode,
                label = { Text(label) }
            )
        }
    }
}
