package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.statistics.domain.StatisticsDateUtils
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import java.time.LocalDate
import java.util.Calendar

@Composable
fun PatientsTab(
    patients: List<Patient>,
    allVisits: List<Vaccination>,
    showFilters: Boolean = false,
    onMilestoneClick: (String) -> Unit = {}
) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val effectiveRegDates = remember(patients, allVisits) {
        StatisticsDateUtils.computeEffectiveRegistrationDates(patients, allVisits)
    }

    val availableYears = remember(effectiveRegDates) {
        StatisticsUtils.getAvailableFinancialYears(
            effectiveRegDates.values.filterNotNull().map { StatisticsDateUtils.formatDateIST(it) }
        )
    }

    val filteredPatients = remember(patients, effectiveRegDates, filterMode, fyQuarter, selectedMonth) {
        patients.filter {
            StatisticsUtils.isEffectiveDateInFilter(effectiveRegDates[it.id], filterMode, fyQuarter, selectedMonth)
        }
    }

    val isOverall = filterMode == "Overall"
    val (prevFilter, prevQuarter, prevMonth) = remember(filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.getPreviousPeriodFilter(filterMode, fyQuarter, selectedMonth)
    }
    val prevPatients = remember(patients, effectiveRegDates, prevFilter, prevQuarter, prevMonth, isOverall) {
        if (isOverall) emptyList() else patients.filter {
            StatisticsUtils.isEffectiveDateInFilter(effectiveRegDates[it.id], prevFilter, prevQuarter, prevMonth)
        }
    }

    val patientStats = remember(filteredPatients, patients, effectiveRegDates) {
        calculatePatientStats(filteredPatients, patients, effectiveRegDates)
    }
    val prevPatientStats = remember(prevPatients, patients, effectiveRegDates) {
        calculatePatientStats(prevPatients, patients, effectiveRegDates)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
    ) {
        if (showFilters) {
            FilterSection(
                availableYears = availableYears.reversed().map { "20$it" },
                filterMode = filterMode,
                fyQuarter = fyQuarter,
                selectedMonth = selectedMonth,
                onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
                onQuarterChange = { if (filterMode != "Overall") { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 } },
                onMonthChange = { if (fyQuarter != 0 && filterMode != "Overall") { selectedMonth = if (selectedMonth == it) -1 else it } }
            )
            Spacer(modifier = Modifier.height(Spacing.xl))
        }

        PatientsContent(
            patients = filteredPatients,
            allPatients = patients,
            totalPatientsCount = patients.size,
            stats = patientStats,
            prevStats = prevPatientStats,
            onMilestoneClick = onMilestoneClick
        )
    }
}

@Composable
private fun PatientsContent(
    patients: List<Patient>,
    allPatients: List<Patient>,
    totalPatientsCount: Int,
    stats: PatientAnalyticsData,
    prevStats: PatientAnalyticsData,
    onMilestoneClick: (String) -> Unit
) {
    val customColors = LocalCustomColors.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Column(modifier = Modifier.fillMaxWidth()) {
        StatisticsSectionHeader(
            title = "Patient summary",
            subtitle = "Registrations and demographics"
        )
        Spacer(modifier = Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Total",
                value = totalPatientsCount.toString(),
                accentColor = MaterialTheme.colorScheme.primary,
                supportingText = "all records"
            )
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "In Period",
                value = stats.newPatientsInPeriod.toString(),
                accentColor = customColors.textSuccess,
                supportingText = "selected period",
                growthPercentage = StatisticsUtils.calculateGrowth(
                    stats.newPatientsInPeriod.toDouble(),
                    prevStats.newPatientsInPeriod.toDouble()
                )
            )
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Registered",
                value = stats.newPatientsToday.toString(),
                accentColor = customColors.textPurple,
                supportingText = "today"
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Girls",
                value = stats.femaleCount.toString(),
                accentColor = customColors.textBlue,
                supportingText = "female"
            )
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Boys",
                value = stats.maleCount.toString(),
                accentColor = customColors.textGreen,
                supportingText = "male"
            )
            SummaryCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = "Other",
                value = stats.otherCount.toString(),
                accentColor = customColors.textOrange,
                supportingText = "other gender"
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        StatisticsSectionHeader(
            title = "Gender distribution",
            subtitle = "Patients in the selected period"
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        GenderDistributionCard(stats = stats, darkTheme = darkTheme)

        Spacer(modifier = Modifier.height(Spacing.xl))

        StatisticsSectionHeader(
            title = "Age group distribution",
            subtitle = "Patients per age band"
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        AgeDistributionCard(ageGroups = stats.ageGroups, totalPatients = patients.size)

        Spacer(modifier = Modifier.height(Spacing.xl))

        StatisticsSectionHeader(
            title = "Upcoming milestones",
            subtitle = "Next 2 months"
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        UpcomingAgeMilestonesSection(patients = allPatients, onMilestoneClick = onMilestoneClick)
    }
}

@Composable
private fun GenderDistributionCard(stats: PatientAnalyticsData, darkTheme: Boolean) {
    val total = stats.maleCount + stats.femaleCount + stats.otherCount + stats.unknownCount
    val femaleColor = if (darkTheme) ChartPatientsDark else ChartPatients
    val maleColor = if (darkTheme) ChartConsultationsDark else ChartConsultations
    val otherColor = if (darkTheme) ChartVaccinationsDark else ChartVaccinations
    val unknownColor = LocalCustomColors.current.textGrey

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StatTileShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
        )
    ) {
        if (total == 0) {
            Text(
                text = "No patient data for this period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.xl)
            )
        } else {
            Row(
                modifier = Modifier.padding(Spacing.card),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val slices = listOf(
                        stats.femaleCount to femaleColor,
                        stats.maleCount to maleColor,
                        stats.otherCount to otherColor,
                        stats.unknownCount to unknownColor
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                contentDescription = "Gender distribution. Female ${stats.femaleCount}, male ${stats.maleCount}, other ${stats.otherCount}, unknown ${stats.unknownCount}."
                            }
                    ) {
                        val stroke = 18.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(
                            color = Color.Gray.copy(alpha = 0.12f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke)
                        )
                        var start = -90f
                        slices.forEach { (count, color) ->
                            if (count > 0) {
                                val sweep = 360f * count / total
                                drawArc(
                                    color = color,
                                    startAngle = start,
                                    sweepAngle = (sweep - 1.5f).coerceAtLeast(0f),
                                    useCenter = false,
                                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = stroke)
                                )
                                start += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = total.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "patients",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.lg))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    GenderLegendItem("Female", stats.femaleCount, femaleColor, total)
                    GenderLegendItem("Male", stats.maleCount, maleColor, total)
                    GenderLegendItem("Other", stats.otherCount, otherColor, total)
                    GenderLegendItem("Unknown", stats.unknownCount, unknownColor, total)
                }
            }
        }
    }
}

@Composable
private fun GenderLegendItem(label: String, count: Int, color: Color, total: Int) {
    val percentage = if (total == 0) 0.0 else count * 100.0 / total
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(Spacing.sm)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$count (${String.format(java.util.Locale.US, "%.1f", percentage)}%)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun AgeDistributionCard(ageGroups: Map<String, Int>, totalPatients: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = StatTileShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            ageGroups.forEach { (label, count) ->
                val progress = if (totalPatients == 0) 0f else count.toFloat() / totalPatients
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpcomingAgeMilestonesSection(patients: List<Patient>, onMilestoneClick: (String) -> Unit) {
    val today = StatisticsDateUtils.todayIST()
    val windowEnd = (today.clone() as Calendar).apply { add(Calendar.MONTH, 2) }

    val counts = remember(patients) {
        val map = mutableMapOf<String, Int>()
        patients.forEach { patient ->
            val milestone = PatientUtils.getNextAgeMilestone(patient.dob, today, windowEnd)
            if (milestone != null) {
                map[milestone.label] = (map[milestone.label] ?: 0) + 1
            } else if (PatientUtils.isOlderThanMonths(patient.dob, 19, today)) {
                map["Older"] = (map["Older"] ?: 0) + 1
            }
        }
        map
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        StatisticsUtils.ageMilestones.forEach { (key, label) ->
            val count = counts[label] ?: 0
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onMilestoneClick(key) }
                    .semantics {
                        contentDescription = "$label milestone, $count patients. Open list."
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private data class PatientAnalyticsData(
    val newPatientsToday: Int,
    val newPatientsInPeriod: Int,
    val maleCount: Int,
    val femaleCount: Int,
    val otherCount: Int,
    val unknownCount: Int,
    val ageGroups: Map<String, Int>
)

private fun calculatePatientStats(patients: List<Patient>, allPatients: List<Patient>, effectiveRegDates: Map<String, LocalDate?>): PatientAnalyticsData {
    val todayIST = StatisticsDateUtils.todayISTString()

    var male = 0
    var female = 0
    var other = 0
    var unknown = 0

    val ageMap = mutableMapOf(
        "0-6 Weeks" to 0, ">6-14 Weeks" to 0, ">14 Weeks-9 Months" to 0,
        ">9-18 Months" to 0, ">18m-5y" to 0, "Above 5y" to 0, "Invalid / Unknown" to 0
    )

    val todayCal = StatisticsDateUtils.todayIST()

    patients.forEach { p ->
        when {
            p.gender.equals("Male", true) -> male++
            p.gender.equals("Female", true) -> female++
            p.gender.equals("Other", true) -> other++
            else -> unknown++
        }

        val dob = PatientUtils.parseDate(p.dob)
        if (dob == null) {
            ageMap["Invalid / Unknown"] = ageMap["Invalid / Unknown"]!! + 1
        } else {
            val dobCal = Calendar.getInstance().apply { time = dob }
            if (dobCal.after(todayCal)) {
                ageMap["Invalid / Unknown"] = ageMap["Invalid / Unknown"]!! + 1
            } else {
                val sixWeeks = (dobCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 42) }
                val fourteenWeeks = (dobCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 98) }
                val nineMonths = (dobCal.clone() as Calendar).apply { add(Calendar.MONTH, 9) }
                val eighteenMonths = (dobCal.clone() as Calendar).apply { add(Calendar.MONTH, 18) }
                val fiveYears = (dobCal.clone() as Calendar).apply { add(Calendar.YEAR, 5) }
                when {
                    !todayCal.after(sixWeeks) -> ageMap["0-6 Weeks"] = ageMap["0-6 Weeks"]!! + 1
                    !todayCal.after(fourteenWeeks) -> ageMap[">6-14 Weeks"] = ageMap[">6-14 Weeks"]!! + 1
                    !todayCal.after(nineMonths) -> ageMap[">14 Weeks-9 Months"] = ageMap[">14 Weeks-9 Months"]!! + 1
                    !todayCal.after(eighteenMonths) -> ageMap[">9-18 Months"] = ageMap[">9-18 Months"]!! + 1
                    !todayCal.after(fiveYears) -> ageMap[">18m-5y"] = ageMap[">18m-5y"]!! + 1
                    else -> ageMap["Above 5y"] = ageMap["Above 5y"]!! + 1
                }
            }
        }
    }

    // "Registered Today" uses effective registration date in IST
    val newTodayAll = allPatients.count { effDate ->
        val ed = effectiveRegDates[effDate.id]
        ed != null && StatisticsDateUtils.formatDateIST(ed) == todayIST
    }
    val inPeriod = patients.size

    return PatientAnalyticsData(newTodayAll, inPeriod, male, female, other, unknown, ageMap)
}
