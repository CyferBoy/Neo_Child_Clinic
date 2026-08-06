package com.neochildclinic.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val patientStats = remember(patients) { calculatePatientStats(patients) }

    PatientsContent(patients = patients, stats = patientStats)
}

@Composable
private fun PatientsContent(patients: List<Patient>, stats: PatientAnalyticsData) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Patient Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            StatCardSmall(Modifier.weight(1f), "Total Patients", patients.size.toString(), Icons.Default.People, Color(0xFF2196F3))
            Spacer(modifier = Modifier.width(12.dp))
            StatCardSmall(Modifier.weight(1f), "New This Month", stats.newPatientsThisMonth.toString(), Icons.Default.PersonAdd, Color(0xFF4CAF50))
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCardSmall(Modifier.weight(1f), "New Today", stats.newPatientsToday.toString(), Icons.Default.Today, Color(0xFF9C27B0))
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        GenderDistributionCard(stats = stats)

        Spacer(modifier = Modifier.height(24.dp))

        AgeDistributionSection(ageGroups = stats.ageGroups, totalPatients = patients.size)
    }
}

@Composable
private fun GenderDistributionCard(stats: PatientAnalyticsData) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gender Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            SimplePieChart(
                data = listOf(stats.maleCount.toFloat(), stats.femaleCount.toFloat(), stats.otherCount.toFloat()),
                colors = listOf(Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFF9E9E9E)),
                labels = listOf("Male", "Female", "Other")
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

private data class PatientAnalyticsData(
    val newPatientsToday: Int,
    val newPatientsThisMonth: Int,
    val maleCount: Int,
    val femaleCount: Int,
    val otherCount: Int,
    val ageGroups: Map<String, Int>
)

private fun calculatePatientStats(patients: List<Patient>): PatientAnalyticsData {
    val today = Calendar.getInstance()
    val currentMonth = today.get(Calendar.MONTH)
    val currentYear = today.get(Calendar.YEAR)
    val todayStr = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())

    var newToday = 0
    var newMonth = 0
    var male = 0
    var female = 0
    
    val ageMap = mutableMapOf(
        "0-6 Weeks" to 0, "6-14 Weeks" to 0, "3-9 Months" to 0,
        "9-18 Months" to 0, "18m - 5y" to 0, "Above 5y" to 0
    )

    patients.forEach { p ->
        if (p.registrationDate == todayStr) newToday++
        
        PatientUtils.parseDate(p.registrationDate ?: "")?.let { date ->
            val c = Calendar.getInstance().apply { time = date }
            if (c.get(Calendar.MONTH) == currentMonth && c.get(Calendar.YEAR) == currentYear) newMonth++
        }

        when {
            p.gender.equals("Male", true) -> male++
            p.gender.equals("Female", true) -> female++
        }

        PatientUtils.parseDate(p.dob)?.let { dob ->
            val diffMs = Date().time - dob.time
            val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()
            when {
                diffDays <= 42 -> ageMap["0-6 Weeks"] = ageMap["0-6 Weeks"]!! + 1
                diffDays <= 98 -> ageMap["6-14 Weeks"] = ageMap["6-14 Weeks"]!! + 1
                diffDays <= 270 -> ageMap["3-9 Months"] = ageMap["3-9 Months"]!! + 1
                diffDays <= 540 -> ageMap["9-18 Months"] = ageMap["9-18 Months"]!! + 1
                diffDays <= 1825 -> ageMap["18m - 5y"] = ageMap["18m - 5y"]!! + 1
                else -> ageMap["Above 5y"] = ageMap["Above 5y"]!! + 1
            }
        }
    }

    return PatientAnalyticsData(newToday, newMonth, male, female, patients.size - male - female, ageMap)
}

@Preview(showBackground = true)
@Composable
private fun PatientsTabPreview() {
    NeoChildTheme {
        PatientsContent(
            patients = emptyList(),
            stats = PatientAnalyticsData(1, 5, 10, 10, 0, mapOf("0-6 Weeks" to 5))
        )
    }
}
