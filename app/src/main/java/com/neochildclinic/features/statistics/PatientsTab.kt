package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.core.utils.PatientUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PatientsTab(patients: List<Patient>) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }
    val availableYears = remember(patients) { StatisticsUtils.getAvailableFinancialYears(patients.map { it.registrationDate ?: "" }) }
    
    // Current period
    val filteredPatients = remember(patients, filterMode, fyQuarter, selectedMonth) {
        patients.filter { StatisticsUtils.isDateInFilter(it.registrationDate ?: "", filterMode, fyQuarter, selectedMonth) }
    }
    
    // Previous period
    val (prevFilter, prevQuarter, prevMonth) = remember(filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.getPreviousPeriodFilter(filterMode, fyQuarter, selectedMonth)
    }
    val prevPatients = remember(patients, prevFilter, prevQuarter, prevMonth) {
        patients.filter { StatisticsUtils.isDateInFilter(it.registrationDate ?: "", prevFilter, prevQuarter, prevMonth) }
    }

    val patientStats = remember(filteredPatients, patients) { calculatePatientStats(filteredPatients, patients) }
    val prevPatientStats = remember(prevPatients, patients) { calculatePatientStats(prevPatients, patients) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        FilterSection(
            availableYears = availableYears.reversed().map { "20$it" },
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onFilterModeChange = { filterMode = "FY ${it.takeLast(5)}"; fyQuarter = 0; selectedMonth = -1 },
            onQuarterChange = { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 },
            onMonthChange = { selectedMonth = if (selectedMonth == it) -1 else it }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        PatientsContent(
            patients = filteredPatients,
            totalPatientsCount = patients.size,
            stats = patientStats,
            prevStats = prevPatientStats
        )
    }
}

@Composable
private fun PatientsContent(
    patients: List<Patient>,
    totalPatientsCount: Int,
    stats: PatientAnalyticsData,
    prevStats: PatientAnalyticsData
) {
    val customColors = LocalCustomColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        
        Spacer(modifier = Modifier.height(12.dp))
        
        SummaryCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Registered Today",
            value = stats.newPatientsToday.toString(),
            icon = Icons.Default.Today,
            iconColor = customColors.textPurple,
            iconBackground = customColors.softPurple
        )

        Spacer(modifier = Modifier.height(24.dp))
        
        GenderDistributionCard(stats = stats)

        Spacer(modifier = Modifier.height(24.dp))

        AgeDistributionSection(ageGroups = stats.ageGroups, totalPatients = patients.size)

        Spacer(modifier = Modifier.height(24.dp))

        UpcomingAgeMilestonesSection(patients = patients)
    }
}

@Composable
private fun GenderDistributionCard(stats: PatientAnalyticsData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gender Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
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
        // Simple visualization
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$percentage%", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AgeDistributionSection(ageGroups: Map<String, Int>, totalPatients: Int) {
    Text("Age Group Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))
    
    ageGroups.forEach { (label, count) ->
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text("$count", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
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
private fun UpcomingAgeMilestonesSection(patients: List<Patient>) {
    val today = Calendar.getInstance()
    val windowEnd = Calendar.getInstance().apply { add(Calendar.MONTH, 2) }
    val milestoneOrder = listOf(
        "6 Weeks", "10 Weeks", "14 Weeks", "6 Months", "7 Months",
        "9 Months", "12 Months", "13 Months", "15 Months", "16–17 Months", "18 Months"
    )
    val grouped = remember(patients, today.get(Calendar.YEAR), today.get(Calendar.DAY_OF_YEAR)) {
        patients.mapNotNull { patient ->
            val milestone = PatientUtils.getNextAgeMilestone(patient.dob, today, windowEnd) ?: return@mapNotNull null
            val age = PatientUtils.calculateExactAge(patient.dob, today) ?: return@mapNotNull null
            Triple(milestone, patient.name, age)
        }.groupBy { it.first.label }
    }

    if (grouped.isEmpty()) return

    Text("Upcoming Age Milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))

    milestoneOrder.forEach { label ->
        val entries = grouped[label].orEmpty().sortedBy { it.first.date.timeInMillis }
        if (entries.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    entries.forEachIndexed { index, (_, name, age) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(age, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (index < entries.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    availableYears: List<String>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Financial Year Dropdown
        var yearExpanded by remember { mutableStateOf(false) }
        val currentFY = filterMode.substringAfter("FY ").let { "20$it" }
        ExposedDropdownMenuBox(
            expanded = yearExpanded,
            onExpandedChange = { yearExpanded = it },
            modifier = Modifier.weight(1.3f)
        ) {
            OutlinedTextField(
                value = "Financial Year  $currentFY",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            ExposedDropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                availableYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year) },
                        onClick = { onFilterModeChange(year); yearExpanded = false }
                    )
                }
            }
        }

        // Quarter Dropdown
        var qExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = qExpanded,
            onExpandedChange = { qExpanded = it },
            modifier = Modifier.weight(0.9f)
        ) {
            OutlinedTextField(
                value = if (fyQuarter == 0) "Quarter  All" else "Quarter  Q$fyQuarter",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
            ExposedDropdownMenu(expanded = qExpanded, onDismissRequest = { qExpanded = false }) {
                DropdownMenuItem(text = { Text("All") }, onClick = { onQuarterChange(0); qExpanded = false })
                (1..4).forEach { q ->
                    DropdownMenuItem(text = { Text("Q$q") }, onClick = { onQuarterChange(q); qExpanded = false })
                }
            }
        }

        // Month Dropdown
        var mExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = mExpanded,
            onExpandedChange = { mExpanded = it },
            modifier = Modifier.weight(0.8f)
        ) {
            OutlinedTextField(
                value = if (selectedMonth == -1) "Month  All" else "Month  ${StatisticsUtils.monthNames[selectedMonth]}",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
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

private data class PatientAnalyticsData(
    val newPatientsToday: Int,
    val newPatientsInPeriod: Int,
    val maleCount: Int,
    val femaleCount: Int,
    val otherCount: Int,
    val unknownCount: Int,
    val ageGroups: Map<String, Int>
)

private fun calculatePatientStats(patients: List<Patient>, allPatients: List<Patient>): PatientAnalyticsData {
    val todayStr = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())

    var male = 0
    var female = 0
    var other = 0
    var unknown = 0
    
    val ageMap = mutableMapOf(
        "0-6 Weeks" to 0, ">6-14 Weeks" to 0, ">14 Weeks-9 Months" to 0,
        ">9-18 Months" to 0, ">18m-5y" to 0, "Above 5y" to 0, "Invalid / Unknown" to 0
    )

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
            val now = Calendar.getInstance()
            if (dobCal.after(now)) {
                ageMap["Invalid / Unknown"] = ageMap["Invalid / Unknown"]!! + 1
            } else {
                val sixWeeks = (dobCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 42) }
                val fourteenWeeks = (dobCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 98) }
                val nineMonths = (dobCal.clone() as Calendar).apply { add(Calendar.MONTH, 9) }
                val eighteenMonths = (dobCal.clone() as Calendar).apply { add(Calendar.MONTH, 18) }
                val fiveYears = (dobCal.clone() as Calendar).apply { add(Calendar.YEAR, 5) }
                when {
                    !now.after(sixWeeks) -> ageMap["0-6 Weeks"] = ageMap["0-6 Weeks"]!! + 1
                    !now.after(fourteenWeeks) -> ageMap[">6-14 Weeks"] = ageMap[">6-14 Weeks"]!! + 1
                    !now.after(nineMonths) -> ageMap[">14 Weeks-9 Months"] = ageMap[">14 Weeks-9 Months"]!! + 1
                    !now.after(eighteenMonths) -> ageMap[">9-18 Months"] = ageMap[">9-18 Months"]!! + 1
                    !now.after(fiveYears) -> ageMap[">18m-5y"] = ageMap[">18m-5y"]!! + 1
                    else -> ageMap["Above 5y"] = ageMap["Above 5y"]!! + 1
                }
            }
        }
    }

    val newTodayAll = allPatients.count { it.registrationDate == todayStr }
    val inPeriod = patients.size

    return PatientAnalyticsData(newTodayAll, inPeriod, male, female, other, unknown, ageMap)
}
