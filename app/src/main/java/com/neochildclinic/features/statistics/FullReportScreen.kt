package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppPullToRefresh
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

    LaunchedEffect(filterMode, fyQuarter, selectedMonth) {
        viewModel.updateFilter(filterMode, fyQuarter, selectedMonth)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Full Report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                    FullReportFilterSection(
                        filterMode = filterMode,
                        fyQuarter = fyQuarter,
                        selectedMonth = selectedMonth,
                        onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
                        onQuarterChange = { if (filterMode != "Overall") { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 } },
                        onMonthChange = { if (fyQuarter != 0 && filterMode != "Overall") { selectedMonth = if (selectedMonth == it) -1 else it } }
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
                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            BarLineChart(
                                title = "Patient Activity — ${uiState.periodLabel}",
                                labels = labels,
                                series = listOf(
                                    BarLineSeries("Patients", uiState.dataPoints.map { it.patients }, ChartPatients),
                                    BarLineSeries("Consultations", uiState.dataPoints.map { it.consultations }, ChartConsultations),
                                    BarLineSeries("Vaccinations", uiState.dataPoints.map { it.vaccinations }, ChartVaccinations, isLine = true)
                                )
                            )

                            BarLineChart(
                                title = "Financial Overview — ${uiState.periodLabel}",
                                labels = labels,
                                series = listOf(
                                    BarLineSeries("Revenue", uiState.dataPoints.map { it.revenue }, ChartRevenue),
                                    BarLineSeries("Online", uiState.dataPoints.map { it.online }, ChartOnline),
                                    BarLineSeries("Cash", uiState.dataPoints.map { it.cash }, ChartCash),
                                    BarLineSeries("Net Profit", uiState.dataPoints.map { it.netProfit }, ChartNetProfit, isLine = true)
                                )
                            )

                            BarLineChart(
                                title = "Financial Breakdown — ${uiState.periodLabel}",
                                labels = labels,
                                series = listOf(
                                    BarLineSeries("Revenue", uiState.dataPoints.map { it.revenue }, ChartRevenue),
                                    BarLineSeries("COGS", uiState.dataPoints.map { it.cogs }, ChartCOGS),
                                    BarLineSeries("Expenses", uiState.dataPoints.map { it.expenses }, ChartExpenses),
                                    BarLineSeries("Net Profit", uiState.dataPoints.map { it.netProfit }, ChartNetProfit, isLine = true)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullReportFilterSection(
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    val disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val quarterEnabled = filterMode != "Overall"
    val monthEnabled = filterMode != "Overall" && fyQuarter != 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var yearExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = yearExpanded,
            onExpandedChange = { yearExpanded = it },
            modifier = Modifier.weight(1.3f)
        ) {
            OutlinedTextField(
                value = "Financial Year  ${StatisticsUtils.displayFilterMode(filterMode)}",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                DropdownMenuItem(text = { Text("Overall") }, onClick = { onFilterModeChange("Overall"); yearExpanded = false })
                val years = listOf("25-26", "24-25", "23-24")
                years.forEach { year ->
                    DropdownMenuItem(text = { Text("20$year") }, onClick = { onFilterModeChange("FY $year"); yearExpanded = false })
                }
            }
        }

        var qExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = qExpanded && quarterEnabled,
            onExpandedChange = { if (quarterEnabled) qExpanded = it },
            modifier = Modifier.weight(0.9f)
        ) {
            OutlinedTextField(
                value = if (fyQuarter == 0) "Quarter  All" else "Quarter  Q$fyQuarter",
                onValueChange = {},
                readOnly = true,
                enabled = quarterEnabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qExpanded && quarterEnabled) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    disabledContainerColor = disabledContainerColor,
                    disabledTextColor = disabledTextColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            if (quarterEnabled) {
                ExposedDropdownMenu(expanded = qExpanded, onDismissRequest = { qExpanded = false }) {
                    DropdownMenuItem(text = { Text("All") }, onClick = { onQuarterChange(0); qExpanded = false })
                    (1..4).forEach { q ->
                        DropdownMenuItem(text = { Text("Q$q") }, onClick = { onQuarterChange(q); qExpanded = false })
                    }
                }
            }
        }

        var mExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = mExpanded && monthEnabled,
            onExpandedChange = { if (monthEnabled) mExpanded = it },
            modifier = Modifier.weight(0.8f)
        ) {
            OutlinedTextField(
                value = if (selectedMonth == -1) "Month  All" else "Month  ${StatisticsUtils.monthNames[selectedMonth]}",
                onValueChange = {},
                readOnly = true,
                enabled = monthEnabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mExpanded && monthEnabled) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    disabledContainerColor = disabledContainerColor,
                    disabledTextColor = disabledTextColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            if (monthEnabled) {
                ExposedDropdownMenu(expanded = mExpanded, onDismissRequest = { mExpanded = false }) {
                    DropdownMenuItem(text = { Text("All") }, onClick = { onMonthChange(-1); mExpanded = false })
                    val months = if (fyQuarter == 0) (0..11).toList() else StatisticsUtils.fyQuarters[fyQuarter - 1].second
                    months.forEach { mIdx ->
                        DropdownMenuItem(text = { Text(StatisticsUtils.monthNames[mIdx]) }, onClick = { onMonthChange(mIdx); mExpanded = false })
                    }
                }
            }
        }
    }
}
