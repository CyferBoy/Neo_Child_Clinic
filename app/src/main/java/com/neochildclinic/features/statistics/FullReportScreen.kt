package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
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

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = { Text("Full Report", fontWeight = FontWeight.Bold) },
                onBack = onBack,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 16.dp)
            ) {
                item {
                    FilterSection(
                        availableYears = uiState.availableFinancialYears.map { "20$it" },
                        filterMode = filterMode,
                        fyQuarter = fyQuarter,
                        selectedMonth = selectedMonth,
                        onFilterModeChange = { filterMode = "FY ${it.takeLast(5)}"; fyQuarter = 0; selectedMonth = -1 },
                        onQuarterChange = { if (filterMode != "Overall") { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 } },
                        onMonthChange = { if (fyQuarter != 0 && filterMode != "Overall") { selectedMonth = if (selectedMonth == it) -1 else it } }
                    )
                }

                item {
                    ChartModeToggle(
                        chartMode = chartMode,
                        onModeSelected = {
                            chartMode = it
                            viewModel.setChartMode(it)
                        }
                    )
                }

                item {
                    if (uiState.isLoading) {
                        Column {
                            SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 280.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 280.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 280.dp)
                        }
                    } else {
                        val labels = uiState.dataPoints.map { it.label }
                        val isLine = chartMode == ChartMode.LINE
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            BarLineChart(
                                title = "Patient Activity \u2014 ${uiState.periodLabel}",
                                labels = labels,
                                series = listOf(
                                    BarLineSeries("Patients", uiState.dataPoints.map { it.patients }, ChartPatients, isLine = isLine),
                                    BarLineSeries("Consultations", uiState.dataPoints.map { it.consultations }, ChartConsultations, isLine = isLine),
                                    BarLineSeries("Vaccinations", uiState.dataPoints.map { it.vaccinations }, ChartVaccinations, isLine = isLine)
                                )
                            )

                            BarLineChart(
                                title = "Financial Overview \u2014 ${uiState.periodLabel}",
                                labels = labels,
                                series = listOf(
                                    BarLineSeries("Revenue", uiState.dataPoints.map { it.revenue }, ChartRevenue, isLine = isLine),
                                    BarLineSeries("Online", uiState.dataPoints.map { it.online }, ChartOnline, isLine = isLine),
                                    BarLineSeries("Cash", uiState.dataPoints.map { it.cash }, ChartCash, isLine = isLine),
                                    BarLineSeries("Net Profit", uiState.dataPoints.map { it.netProfit }, ChartNetProfit, isLine = isLine)
                                )
                            )

                            BarLineChart(
                                title = "Financial Breakdown \u2014 ${uiState.periodLabel}",
                                labels = labels,
                                series = listOf(
                                    BarLineSeries("Revenue", uiState.dataPoints.map { it.revenue }, ChartRevenue, isLine = isLine),
                                    BarLineSeries("COGS", uiState.dataPoints.map { it.cogs }, ChartCOGS, isLine = isLine),
                                    BarLineSeries("Expenses", uiState.dataPoints.map { it.expenses }, ChartExpenses, isLine = isLine),
                                    BarLineSeries("Net Profit", uiState.dataPoints.map { it.netProfit }, ChartNetProfit, isLine = isLine)
                                )
                            )
                        }
                    }
                }
            }
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

