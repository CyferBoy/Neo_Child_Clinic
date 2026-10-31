package com.neochildclinic.feature.statistics.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.common.CurrencyUtils
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.SkeletonCard
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullReportScreen(
    onBack: () -> Unit,
    viewModel: FullReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var filterMode by rememberSaveable { mutableStateOf(StatisticsUtils.getCurrentFinancialYear()) }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }
    var showFilter by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(filterMode, fyQuarter, selectedMonth) {
        viewModel.updateFilter(filterMode, fyQuarter, selectedMonth)
    }

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val inkPrimary = if (darkTheme) InkPrimaryDark else InkPrimaryLight
    val inkMuted = if (darkTheme) InkMutedDark else InkMutedLight
    val outline = if (darkTheme) OutlineDark else OutlineLight
    val surface = if (darkTheme) SurfaceDark else SurfaceLight
    val surfaceAlt = if (darkTheme) SurfaceAltDark else SurfaceAltLight
    val clinicBlue = if (darkTheme) ClinicBlueDark else ClinicBlue

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = {
                        Text(
                            text = "Full Report",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = inkPrimary
                            )
                        )
                    },
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showFilter = !showFilter }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = if (showFilter) "Hide filters" else "Show filters",
                                tint = if (showFilter) clinicBlue else inkPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    FullReportPdfExporter.exportPdf(context, uiState.periodLabel, uiState.summary)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export report",
                                tint = inkPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                FullReportPdfExporter.printReport(context, uiState.periodLabel, uiState.summary)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Print report",
                                tint = inkPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = inkPrimary,
                        navigationIconContentColor = inkPrimary
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
                        top = Spacing.xs,
                        bottom = Spacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Period Subtitle
                    item {
                        Text(
                            text = "${uiState.periodLabel.ifBlank { "October 2026" }} · clinic-wide",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = inkMuted
                        )
                    }

                    // Collapsible Filter Section
                    item {
                        AnimatedVisibility(
                            visible = showFilter,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                                FilterSection(
                                    availableYears = uiState.availableFinancialYears.map { "20$it" },
                                    filterMode = filterMode,
                                    fyQuarter = fyQuarter,
                                    selectedMonth = selectedMonth,
                                    onFilterModeChange = {
                                        filterMode = it
                                        fyQuarter = 0
                                        selectedMonth = -1
                                    },
                                    onQuarterChange = {
                                        if (filterMode != "Overall") {
                                            fyQuarter = if (fyQuarter == it) 0 else it
                                            selectedMonth = -1
                                        }
                                    },
                                    onMonthChange = {
                                        if (fyQuarter != 0 && filterMode != "Overall") {
                                            selectedMonth = if (selectedMonth == it) -1 else it
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Hero KPI Banner (58dp)
                    item {
                        val heroBg = if (darkTheme) Color(0xFF123E6B) else Color(0xFFDCE9F8)
                        val heroContentColor = if (darkTheme) Color(0xFFCFE3FF) else Color(0xFF0A2F52)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(heroBg)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Canvas(modifier = Modifier.size(22.dp)) {
                                        val strokeW = 1.8.dp.toPx()
                                        drawArc(
                                            color = heroContentColor,
                                            startAngle = -90f,
                                            sweepAngle = 270f,
                                            useCenter = true,
                                            style = Stroke(
                                                width = strokeW,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Net profit",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = heroContentColor.copy(alpha = 0.75f)
                                        )
                                        Text(
                                            text = CurrencyUtils.formatInr(uiState.summary.netProfit),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = heroContentColor
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Revenue ${CurrencyUtils.formatInr(uiState.summary.totalRevenue)}",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = heroContentColor.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Expenses ${CurrencyUtils.formatInr(uiState.summary.totalExpenses)}",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = heroContentColor.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Segmented Control (38dp)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(surfaceAlt)
                                .border(1.dp, outline, RoundedCornerShape(12.dp))
                                .padding(3.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                val tabs = listOf(
                                    "Table" to ReportViewMode.TABLE,
                                    "Line" to ReportViewMode.LINE,
                                    "Bar" to ReportViewMode.BAR
                                )
                                tabs.forEach { (label, mode) ->
                                    val isSelected = uiState.viewMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(10.dp))
                                            .then(
                                                if (isSelected) {
                                                    Modifier
                                                        .background(surface)
                                                        .border(1.dp, outline, RoundedCornerShape(10.dp))
                                                } else {
                                                    Modifier.clickable { viewModel.setViewMode(mode) }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) inkPrimary else inkMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.errorMessage != null) {
                        item {
                            ErrorBanner(message = uiState.errorMessage ?: "Could not load the report.")
                        }
                    }

                    if (uiState.isLoading) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 186.dp)
                                SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 70.dp)
                                SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 56.dp)
                            }
                        }
                    } else if (uiState.viewMode == ReportViewMode.TABLE) {
                        // Table View (matches SVG 100%)
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Summary",
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = inkPrimary
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(surface)
                                        .border(1.dp, outline, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Column {
                                        FullReportRow(
                                            label = "Total patients",
                                            middle = "${uiState.summary.totalPatients}",
                                            right = "+${uiState.summary.newPatientsInPeriod}",
                                            inkPrimary = inkPrimary,
                                            inkMuted = inkMuted
                                        )
                                        FullReportDivider(outline)
                                        FullReportRow(
                                            label = "Consultations",
                                            middle = "${uiState.summary.consultationCount}",
                                            right = CurrencyUtils.formatInr(uiState.summary.consultationRevenue),
                                            inkPrimary = inkPrimary,
                                            inkMuted = inkMuted
                                        )
                                        FullReportDivider(outline)
                                        FullReportRow(
                                            label = "Vaccinations",
                                            middle = "${uiState.summary.vaccinationCount}",
                                            right = CurrencyUtils.formatInr(uiState.summary.vaccinationRevenue),
                                            inkPrimary = inkPrimary,
                                            inkMuted = inkMuted
                                        )
                                        FullReportDivider(outline)
                                        FullReportRow(
                                            label = "Vaccine COGS",
                                            middle = if (!uiState.summary.isCogsComplete) "—" else "${uiState.summary.vaccinationCount}",
                                            right = if (!uiState.summary.isCogsComplete) "Pending" else CurrencyUtils.formatInr(uiState.summary.cogsAmount),
                                            inkPrimary = inkPrimary,
                                            inkMuted = inkMuted
                                        )
                                        FullReportDivider(outline)
                                        FullReportRow(
                                            label = "Expenses",
                                            middle = "${uiState.summary.expenseItemCount} items",
                                            right = CurrencyUtils.formatInr(uiState.summary.totalExpenses),
                                            inkPrimary = inkPrimary,
                                            inkMuted = inkMuted
                                        )
                                        FullReportDivider(outline)
                                        FullReportRow(
                                            label = "Net profit",
                                            middle = "",
                                            right = CurrencyUtils.formatInr(uiState.summary.netProfit),
                                            isHighlight = true,
                                            highlightColor = if (darkTheme) StatusSuccessDark else StatusSuccessLight,
                                            inkPrimary = inkPrimary,
                                            inkMuted = inkMuted
                                        )
                                    }
                                }

                                if (!uiState.summary.isCogsComplete && uiState.summary.pendingCogsCount > 0) {
                                    Text(
                                        text = "COGS pending for ${uiState.summary.pendingCogsCount} ${if (uiState.summary.pendingCogsCount == 1) "batch" else "batches"} · net profit is provisional",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = if (darkTheme) StatusWarningDark else StatusWarningLight,
                                        modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                                    )
                                }
                            }
                        }

                        // Payment Methods Section
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Payment methods",
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = inkPrimary
                                )

                                val totalInc = uiState.summary.cashTotal + uiState.summary.onlineTotal
                                val cashPercent = if (totalInc > 0) ((uiState.summary.cashTotal / totalInc) * 100).roundToInt() else 0
                                val onlinePercent = if (totalInc > 0) (100 - cashPercent).coerceAtLeast(0) else 0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(surface)
                                        .border(1.dp, outline, RoundedCornerShape(20.dp)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = CurrencyUtils.formatInr(uiState.summary.cashTotal),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = inkPrimary,
                                            letterSpacing = (-0.5).sp
                                        )
                                        Text(
                                            text = "cash",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = inkMuted
                                        )
                                        Text(
                                            text = "$cashPercent%",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = inkMuted
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(42.dp)
                                            .background(outline)
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = CurrencyUtils.formatInr(uiState.summary.onlineTotal),
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = inkPrimary,
                                            letterSpacing = (-0.5).sp
                                        )
                                        Text(
                                            text = "online",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = inkMuted
                                        )
                                        Text(
                                            text = "$onlinePercent%",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = inkMuted
                                        )
                                    }
                                }
                            }
                        }

                        // Export Section
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Export",
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = inkPrimary
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(surface)
                                        .border(1.dp, outline, RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = clinicBlue
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = FullReportPdfExporter.getFileName(uiState.periodLabel.ifBlank { "October 2026" }),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = inkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Generated from local records · ready to share",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = inkMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                FullReportPdfExporter.exportPdf(context, uiState.periodLabel, uiState.summary)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = "Download report",
                                            modifier = Modifier.size(20.dp),
                                            tint = clinicBlue
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom Export Button (52dp)
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        FullReportPdfExporter.exportPdf(context, uiState.periodLabel, uiState.summary)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = clinicBlue,
                                    contentColor = if (darkTheme) Color(0xFF07305B) else Color(0xFFFFFFFF)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Export report",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // Line / Bar Chart Views
                        if (uiState.dataPoints.isEmpty()) {
                            item {
                                StatisticsEmptyState(
                                    icon = Icons.Default.BarChart,
                                    title = "No data for this period",
                                    message = "Change the filters or add clinic records, then refresh."
                                )
                            }
                        } else {
                            item {
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
                                val isLine = uiState.viewMode == ReportViewMode.LINE
                                val expensesK = uiState.dataPoints.map { it.expenses / 1000f }

                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
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
                                        subtitle = "Revenue, payments and profit (₹K)",
                                        labels = labels,
                                        series = listOf(
                                            BarLineSeries("Revenue", uiState.dataPoints.map { it.revenue }, revenueColor, isLine = isLine),
                                            BarLineSeries("Online", uiState.dataPoints.map { it.online }, onlineColor, isLine = isLine),
                                            BarLineSeries("Cash", uiState.dataPoints.map { it.cash }, cashColor, isLine = isLine),
                                            BarLineSeries("Net Profit", uiState.dataPoints.map { it.netProfit }, netProfitColor, isLine = isLine)
                                        ),
                                        yLabel = "₹K"
                                    )

                                    BarLineChart(
                                        title = "Financial breakdown",
                                        subtitle = "Revenue, COGS, expenses and profit (₹K)",
                                        labels = labels,
                                        series = listOf(
                                            BarLineSeries("Revenue", uiState.dataPoints.map { it.revenue }, revenueColor, isLine = isLine),
                                            BarLineSeries("COGS", uiState.dataPoints.map { it.cogs }, cogsColor, isLine = isLine),
                                            BarLineSeries("Expenses", expensesK, expensesColor, isLine = isLine),
                                            BarLineSeries("Net Profit", uiState.dataPoints.map { it.netProfit }, netProfitColor, isLine = isLine)
                                        ),
                                        yLabel = "₹K"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullReportRow(
    label: String,
    middle: String,
    right: String,
    inkPrimary: Color,
    inkMuted: Color,
    isHighlight: Boolean = false,
    highlightColor: Color = inkPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlight) inkPrimary else inkMuted,
            modifier = Modifier.weight(1.2f)
        )
        if (middle.isNotBlank()) {
            Text(
                text = middle,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal,
                color = inkMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.9f)
            )
        } else {
            Spacer(modifier = Modifier.weight(0.9f))
        }
        Text(
            text = right,
            fontSize = 11.sp,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isHighlight) highlightColor else inkPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FullReportDivider(outline: Color) {
    HorizontalDivider(
        thickness = 1.dp,
        color = outline,
        modifier = Modifier.padding(vertical = 2.dp)
    )
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
