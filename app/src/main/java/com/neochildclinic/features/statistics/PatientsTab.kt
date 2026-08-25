package com.neochildclinic.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Today
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
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.core.designsystem.NeoChildTheme
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
    val filteredPatients = remember(patients, filterMode, fyQuarter, selectedMonth) {
        patients.filter { StatisticsUtils.isDateInFilter(it.registrationDate ?: "", filterMode, fyQuarter, selectedMonth) }
    }
    val patientStats = remember(filteredPatients, patients) { calculatePatientStats(filteredPatients, patients) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        PatientsHeader(
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            availableYears = availableYears,
            onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
            onQuarterChange = { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 },
            onMonthChange = { selectedMonth = if (selectedMonth == it) -1 else it }
        )
        PatientsContent(patients = filteredPatients, totalPatients = patients.size, stats = patientStats)
    }
}

@Composable
private fun PatientsContent(patients: List<Patient>, totalPatients: Int, stats: PatientAnalyticsData) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Patient Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatCardSmall(Modifier.weight(1f), "Total Patients", totalPatients.toString(), Icons.Default.People, Color(0xFF2196F3))
            Spacer(modifier = Modifier.width(12.dp))
            StatCardSmall(Modifier.weight(1f), "Registered in Period", stats.newPatientsThisMonth.toString(), Icons.Default.PersonAdd, Color(0xFF4CAF50))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCardSmall(Modifier.weight(1f), "Registered Today", stats.newPatientsToday.toString(), Icons.Default.Today, Color(0xFF9C27B0))
            Spacer(modifier = Modifier.weight(1f))
        }

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
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gender Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            SimplePieChart(
                data = listOf(stats.maleCount.toFloat(), stats.femaleCount.toFloat(), stats.otherCount.toFloat(), stats.unknownCount.toFloat()),
                colors = listOf(Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF9E9E9E), Color(0xFF757575)),
                labels = listOf("Male", "Female", "Other", "Unknown")
            )
        }
    }
}

@Composable
private fun AgeDistributionSection(ageGroups: Map<String, Int>, totalPatients: Int) {
    Text("Age Group Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))
    
    ageGroups.forEach { (label, count) ->
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text("$count", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            val progress = if (totalPatients == 0) 0f else count.toFloat() / totalPatients
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
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
    Spacer(modifier = Modifier.height(12.dp))

    milestoneOrder.forEach { label ->
        val entries = grouped[label].orEmpty().sortedBy { it.first.date.timeInMillis }
        if (entries.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    entries.forEachIndexed { index, (_, name, age) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(age, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (index < entries.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientsHeader(
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    availableYears: List<String>,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    val options = listOf("Overall") + availableYears.reversed().map { "FY $it" }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Patient Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        filterMode == "Overall" -> "All Time"
                        selectedMonth != -1 -> "${StatisticsUtils.monthNames[selectedMonth]} Statistics"
                        fyQuarter != 0 -> "Quarter $fyQuarter Statistics"
                        else -> "Annual Statistics ($filterMode)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            var expanded by remember { mutableStateOf(false) }
            Box {
                AssistChip(onClick = { expanded = true }, label = { Text(filterMode) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onFilterModeChange(option); expanded = false }) }
                }
            }
        }
        if (filterMode.startsWith("FY ")) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatisticsUtils.fyQuarters.forEachIndexed { index, _ ->
                    val q = index + 1
                    FilterChip(selected = fyQuarter == q, onClick = { onQuarterChange(q) }, label = { Text("Q$q") }, modifier = Modifier.weight(1f))
                }
            }
            if (fyQuarter > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatisticsUtils.fyQuarters[fyQuarter - 1].second.forEach { month ->
                        FilterChip(selected = selectedMonth == month, onClick = { onMonthChange(month) }, label = { Text(StatisticsUtils.monthNames[month]) })
                    }
                }
            }
        }
    }
}

private data class PatientAnalyticsData(
    val newPatientsToday: Int,
    val newPatientsThisMonth: Int,
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
    // "Registered in Period" is exactly the filtered patient population; date validity
    // has already been enforced by StatisticsUtils.isDateInFilter().
    val registeredInPeriod = patients.size

    return PatientAnalyticsData(newTodayAll, registeredInPeriod, male, female, other, unknown, ageMap)
}

@Preview(showBackground = true)
@Composable
private fun PatientsTabPreview() {
    NeoChildTheme {
        PatientsContent(
            patients = emptyList(),
            totalPatients = 0,
            stats = PatientAnalyticsData(1, 5, 10, 10, 0, 0, mapOf("0-6 Weeks" to 5))
        )
    }
}
