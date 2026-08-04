package com.neochildclinic.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.utils.PatientUtils
import java.util.*

@Composable
fun VaccinationsTab(vaccinations: List<Vaccination>) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val availableYears = remember(vaccinations) { StatisticsUtils.getAvailableFinancialYears(vaccinations.map { it.dateGiven }) }

    val filteredVaccinations = remember(vaccinations, filterMode, fyQuarter, selectedMonth) {
        vaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }

    val vaccineStats = remember(filteredVaccinations) { calculateVaccineStats(filteredVaccinations) }

    VaccinationsContent(
        vaccinations = filteredVaccinations,
        stats = vaccineStats,
        filterMode = filterMode,
        fyQuarter = fyQuarter,
        selectedMonth = selectedMonth,
        availableYears = availableYears,
        onFilterModeChange = { 
            filterMode = it
            fyQuarter = 0
            selectedMonth = -1
        },
        onQuarterChange = { 
            fyQuarter = if (fyQuarter == it) 0 else it
            selectedMonth = -1
        },
        onMonthChange = { 
            selectedMonth = if (selectedMonth == it) -1 else it
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccinationsContent(
    vaccinations: List<Vaccination>,
    stats: List<Pair<String, Int>>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    availableYears: List<String>,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    val mainOptions = remember(availableYears) { listOf("Overall") + availableYears.reversed().map { "FY $it" } }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        VaccinationsHeader(
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            mainOptions = mainOptions,
            onFilterModeChange = onFilterModeChange
        )

        if (filterMode.startsWith("FY ")) {
            QuarterAndMonthFilters(
                fyQuarter = fyQuarter,
                selectedMonth = selectedMonth,
                onQuarterChange = onQuarterChange,
                onMonthChange = onMonthChange
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SummaryCards(vaccinations = vaccinations)

        Spacer(modifier = Modifier.height(24.dp))

        VaccineStatsSection(stats = stats)
    }
}

@Composable
private fun VaccinationsHeader(
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    mainOptions: List<String>,
    onFilterModeChange: (String) -> Unit
) {
    val subtitle = remember(filterMode, selectedMonth, fyQuarter) {
        when {
            filterMode == "Overall" -> "All Time"
            selectedMonth != -1 -> "${StatisticsUtils.monthNames[selectedMonth]} Statistics"
            fyQuarter != 0 -> "Quarter ${fyQuarter} Statistics"
            else -> "Annual Statistics ($filterMode)"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Vaccination Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        
        var expanded by remember { mutableStateOf(false) }
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(filterMode) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                mainOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onFilterModeChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuarterAndMonthFilters(
    fyQuarter: Int,
    selectedMonth: Int,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text("Quarters", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatisticsUtils.fyQuarters.forEachIndexed { index, _ ->
                val qNum = index + 1
                FilterChip(
                    selected = fyQuarter == qNum,
                    onClick = { onQuarterChange(qNum) },
                    label = { Text("Q$qNum") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        if (fyQuarter > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Months in Q$fyQuarter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatisticsUtils.fyQuarters[fyQuarter - 1].second.forEach { mIdx ->
                    FilterChip(
                        selected = selectedMonth == mIdx,
                        onClick = { onMonthChange(mIdx) },
                        label = { Text(StatisticsUtils.monthNames[mIdx], style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun SummaryCards(vaccinations: List<Vaccination>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        StatCardSmall(Modifier.weight(1f), "Total Doses", vaccinations.sumOf { it.items.size }.toString(), Icons.Default.FactCheck, Color(0xFF4CAF50))
        Spacer(modifier = Modifier.width(12.dp))
        val avg = remember(vaccinations) { if (vaccinations.isEmpty()) 0.0 else vaccinations.size.toDouble() / 30 }
        StatCardSmall(Modifier.weight(1f), "Avg Per Month", String.format(Locale.getDefault(), "%.1f", avg), Icons.Default.Timeline, Color(0xFF2196F3))
    }
}

@Composable
private fun VaccineStatsSection(stats: List<Pair<String, Int>>) {
    Text("All Administered Vaccines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(12.dp))

    val maxCount = remember(stats) { stats.firstOrNull()?.second ?: 1 }
    stats.forEach { (name, count) ->
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text("$count", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { count.toFloat() / maxCount },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun calculateVaccineStats(vaccinations: List<Vaccination>): List<Pair<String, Int>> {
    val vaccineCounts = mutableMapOf<String, Int>()
    vaccinations.forEach { v ->
        v.items.forEach { item ->
            val cleanName = PatientUtils.cleanVaccineName(item.vaccineName)
            vaccineCounts[cleanName] = (vaccineCounts[cleanName] ?: 0) + 1
        }
    }
    return vaccineCounts.toList().sortedByDescending { it.second }
}

@Preview(showBackground = true)
@Composable
private fun VaccinationsTabPreview() {
    NeoChildTheme {
        VaccinationsContent(
            vaccinations = emptyList(),
            stats = listOf("BCG" to 10, "HepB" to 8),
            filterMode = "Overall",
            fyQuarter = 0,
            selectedMonth = -1,
            availableYears = listOf("2023-24"),
            onFilterModeChange = {},
            onQuarterChange = {},
            onMonthChange = {}
        )
    }
}
