package com.neochildclinic.feature.statistics.presentation
import com.neochildclinic.feature.statistics.domain.StatisticsDateUtils
import com.neochildclinic.feature.statistics.domain.StatisticsUtils

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.common.PatientUtils
import java.time.LocalDate
import java.util.Calendar

@Composable
fun PatientsTab(patients: List<Patient>, allVisits: List<Vaccination>, onMilestoneClick: (String) -> Unit = {}) {
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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).verticalScroll(rememberScrollState()).padding(Spacing.lg)) {
        FilterSection(
            availableYears = availableYears.reversed().map { "20$it" },
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
            onQuarterChange = { if (filterMode != "Overall") { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 } },
            onMonthChange = { if (fyQuarter != 0 && filterMode != "Overall") { selectedMonth = if (selectedMonth == it) -1 else it } },
            modifier = Modifier.padding(bottom = Spacing.sm)
        )

        Spacer(modifier = Modifier.height(Spacing.lg))
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

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(Spacing.lg))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Total Patients",
                value = totalPatientsCount.toString(),
                icon = Icons.Default.People,
                iconColor = customColors.textBlue,
                iconBackground = customColors.softBlue
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "In Period",
                value = stats.newPatientsInPeriod.toString(),
                icon = Icons.Default.PersonAdd,
                iconColor = customColors.textGreen,
                iconBackground = customColors.softGreen,
                growthPercentage = StatisticsUtils.calculateGrowth(stats.newPatientsInPeriod.toDouble(), prevStats.newPatientsInPeriod.toDouble())
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        SummaryCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Registered Today",
            value = stats.newPatientsToday.toString(),
            icon = Icons.Default.Today,
            iconColor = customColors.textPurple,
            iconBackground = customColors.softPurple
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        GenderDistributionCard(stats = stats)

        Spacer(modifier = Modifier.height(Spacing.xl))

        AgeDistributionSection(ageGroups = stats.ageGroups, totalPatients = patients.size)

        Spacer(modifier = Modifier.height(Spacing.xl))

        UpcomingAgeMilestonesSection(patients = allPatients, onMilestoneClick = onMilestoneClick)
    }
}

@Composable
private fun GenderDistributionCard(stats: PatientAnalyticsData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ChartContainerShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gender Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(Spacing.xl))
            SimpleGenderChart(stats = stats)
        }
    }
}

@Composable
private fun SimpleGenderChart(stats: PatientAnalyticsData) {
    val total = (stats.maleCount + stats.femaleCount + stats.otherCount + stats.unknownCount).toFloat()
    if (total == 0f) {
        Text("No Data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GenderLegendItem("Male", stats.maleCount, Color(0xFF2196F3), total)
            GenderLegendItem("Female", stats.femaleCount, Color(0xFFE91E63), total)
            GenderLegendItem("Other", stats.otherCount, Color(0xFF9E9E9E), total)
            GenderLegendItem("Unknown", stats.unknownCount, Color(0xFF757575), total)
        }
    }
}

@Composable
private fun GenderLegendItem(label: String, count: Int, color: Color, total: Float) {
    val percentage = (count / total * 100).toInt()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(200.dp)) {
        Box(modifier = Modifier.size(Spacing.md).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$percentage%", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AgeDistributionSection(ageGroups: Map<String, Int>, totalPatients: Int) {
    Text("Age Group Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(Spacing.lg))

    ageGroups.forEach { (label, count) ->
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text("$count", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            val progress = if (totalPatients == 0) 0f else count.toFloat() / totalPatients
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun UpcomingAgeMilestonesSection(patients: List<Patient>, onMilestoneClick: (String) -> Unit) {
    val today = StatisticsDateUtils.todayIST()
    val windowEnd = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, 2) }

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

    Text("Upcoming Age Milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(Spacing.lg))

    StatisticsUtils.ageMilestones.chunked(3).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            row.forEach { (key, label) ->
                MilestoneCard(
                    modifier = Modifier.weight(1f),
                    label = label,
                    count = counts[label] ?: 0,
                    onClick = { onMilestoneClick(key) }
                )
            }
            repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
        Spacer(modifier = Modifier.height(Spacing.md))
    }
}

@Composable
private fun MilestoneCard(modifier: Modifier = Modifier, label: String, count: Int, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.lg, horizontal = Spacing.sm).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("patients", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val dobCal = java.util.Calendar.getInstance().apply { time = dob }
            if (dobCal.after(todayCal)) {
                ageMap["Invalid / Unknown"] = ageMap["Invalid / Unknown"]!! + 1
            } else {
                val sixWeeks = (dobCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 42) }
                val fourteenWeeks = (dobCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 98) }
                val nineMonths = (dobCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, 9) }
                val eighteenMonths = (dobCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, 18) }
                val fiveYears = (dobCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.YEAR, 5) }
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
