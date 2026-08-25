package com.neochildclinic.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
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
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.utils.PatientUtils
import java.util.*

@Composable
fun VaccinationsTab(vaccinations: List<Vaccination>, vaccinationReminders: List<ReminderEntity> = emptyList()) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val validVaccinations = remember(vaccinations) { StatisticsUtils.filterValidVaccinations(vaccinations) }
    val availableYears = remember(validVaccinations) { StatisticsUtils.getAvailableFinancialYears(validVaccinations.map { it.dateGiven }) }

    val filteredVaccinations = remember(validVaccinations, filterMode, fyQuarter, selectedMonth) {
        validVaccinations.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }

    val vaccineStats = remember(filteredVaccinations) { calculateVaccineStats(filteredVaccinations) }

    VaccinationsContent(
        vaccinations = filteredVaccinations,
        stats = vaccineStats,
        vaccinationReminders = vaccinationReminders,
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
    vaccinationReminders: List<ReminderEntity>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    availableYears: List<String>,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
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

        SummaryCards(vaccinations = vaccinations, filterMode = filterMode, fyQuarter = fyQuarter, selectedMonth = selectedMonth)

        Spacer(modifier = Modifier.height(20.dp))

        VaccinationSectionSelector(
            selected = selectedSection,
            onSelected = { selectedSection = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSection == 0) {
            VaccineStatsSection(stats = stats)
        } else {
            UpcomingVaccineNeedSection(reminders = vaccinationReminders)
        }
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
private fun SummaryCards(vaccinations: List<Vaccination>, filterMode: String, fyQuarter: Int, selectedMonth: Int) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val totalDoses = vaccinations.sumOf { v -> v.items.sumOf { it.quantity.coerceAtLeast(0) } }
        val monthCount = remember(vaccinations, filterMode, fyQuarter, selectedMonth) {
            StatisticsUtils.monthCountForFilter(vaccinations.map { it.dateGiven }, filterMode, fyQuarter, selectedMonth)
        }
        val avg = if (monthCount == 0) 0.0 else totalDoses.toDouble() / monthCount
        StatCardSmall(Modifier.weight(1f), "Total Doses", totalDoses.toString(), Icons.Default.FactCheck, Color(0xFF4CAF50))
        Spacer(modifier = Modifier.width(12.dp))
        StatCardSmall(Modifier.weight(1f), "Avg Doses / Month", String.format(Locale.getDefault(), "%.1f", avg), Icons.Default.Timeline, Color(0xFF2196F3))
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccinationSectionSelector(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selected == 0,
            onClick = { onSelected(0) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text("Administered") }
        )
        SegmentedButton(
            selected = selected == 1,
            onClick = { onSelected(1) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("Upcoming") }
        )
    }
}

private data class UpcomingVaccineTypeStat(
    val type: String,
    val count: Int,
    val brands: List<Pair<String, Int>>
)

@Composable
private fun UpcomingVaccineNeedSection(reminders: List<ReminderEntity>) {
    val stats = remember(reminders) {
        calculateUpcomingVaccineNeeds(reminders)
    }
    var expandedType by rememberSaveable { mutableStateOf<String?>(null) }

    Text(
        "Upcoming Vaccine Need",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (stats.isEmpty()) {
        Text(
            "No upcoming vaccine needs found.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    stats.forEach { stat ->
        val expanded = expandedType == stat.type
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable {
                    expandedType = if (expanded) null else stat.type
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stat.type,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stat.count.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    stat.brands.forEach { (brand, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                brand,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun calculateUpcomingVaccineNeeds(
    reminders: List<ReminderEntity>
): List<UpcomingVaccineTypeStat> {
    val active = reminders.filter {
        it.status == "ACTIVE" &&
            it.reminderEnabled &&
            it.category.equals("VACCINATION", ignoreCase = true)
    }

    return active
        .groupBy { it.type.trim().ifBlank { "Other" } }
        .map { (type, typeReminders) ->
            val brandCounts = mutableMapOf<String, Int>()
            typeReminders.forEach { reminder ->
                reminder.vaccineName
                    .split(",")
                    .map { PatientUtils.cleanVaccineName(it.trim()) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { brand ->
                        brandCounts[brand] = (brandCounts[brand] ?: 0) + 1
                    }
            }

            UpcomingVaccineTypeStat(
                type = type,
                count = typeReminders.size,
                brands = brandCounts.toList().sortedByDescending { it.second }
            )
        }
        .sortedByDescending { it.count }
}

private fun calculateVaccineStats(vaccinations: List<Vaccination>): List<Pair<String, Int>> {
    val vaccineCounts = mutableMapOf<String, Int>()
    vaccinations.forEach { v ->
        v.items.forEachIndexed { index, item ->
            val cleanName = PatientUtils.cleanVaccineName(StatisticsUtils.vaccineName(v, index))
            if (cleanName.isNotBlank()) vaccineCounts[cleanName] = (vaccineCounts[cleanName] ?: 0) + item.quantity.coerceAtLeast(0)
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
            vaccinationReminders = emptyList(),
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
