package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
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
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.utils.PatientUtils
import java.util.*

// A single administered dose, sourced from vaccination_items (never gated on the parent
// visit's status - see StatisticsUtils rules for the Administered segment). dateGiven is
// carried over from the parent vaccination purely to filter/group by period; it is not a
// vaccination_items column.
private data class AdministeredDose(val vaccineName: String, val quantity: Int, val dateGiven: String)

private fun administeredDoses(vaccinations: List<Vaccination>, validVaccineIds: Set<String>): List<AdministeredDose> =
    vaccinations.flatMap { v ->
        v.items
            .filter { it.vaccineName.isNotBlank() && it.vaccineId in validVaccineIds }
            .map { AdministeredDose(PatientUtils.cleanVaccineName(it.vaccineName), it.quantity.coerceAtLeast(0), v.dateGiven) }
    }

@Composable
fun VaccinationsTab(vaccinations: List<Vaccination>, vaccinationReminders: List<ReminderEntity> = emptyList(), vaccines: List<InventoryItem> = emptyList()) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val validVaccineIds = remember(vaccines) { vaccines.map { it.id }.toSet() }
    // All administered doses, independent of visit status - the Administered segment's
    // source of truth is vaccination_items itself, not the parent visit's completion state.
    val allDoses = remember(vaccinations, validVaccineIds) { administeredDoses(vaccinations, validVaccineIds) }
    val availableYears = remember(allDoses) { StatisticsUtils.getAvailableFinancialYears(allDoses.map { it.dateGiven }) }

    // Current period
    val filteredDoses = remember(allDoses, filterMode, fyQuarter, selectedMonth) {
        allDoses.filter { StatisticsUtils.isDateInFilter(it.dateGiven, filterMode, fyQuarter, selectedMonth) }
    }

    // Previous period
    val (prevFilter, prevQuarter, prevMonth) = remember(filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.getPreviousPeriodFilter(filterMode, fyQuarter, selectedMonth)
    }
    val prevDoses = remember(allDoses, prevFilter, prevQuarter, prevMonth) {
        allDoses.filter { StatisticsUtils.isDateInFilter(it.dateGiven, prevFilter, prevQuarter, prevMonth) }
    }

    val vaccineStats = remember(filteredDoses) { calculateVaccineStats(filteredDoses) }

    VaccinationsContent(
        doses = filteredDoses,
        prevDoses = prevDoses,
        stats = vaccineStats,
        vaccinationReminders = vaccinationReminders,
        validVaccineIds = validVaccineIds,
        filterMode = filterMode,
        fyQuarter = fyQuarter,
        selectedMonth = selectedMonth,
        availableYears = availableYears,
        onFilterModeChange = { filterMode = "FY ${it.takeLast(5)}"; fyQuarter = 0; selectedMonth = -1 },
        onQuarterChange = { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 },
        onMonthChange = { selectedMonth = if (selectedMonth == it) -1 else it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccinationsContent(
    doses: List<AdministeredDose>,
    prevDoses: List<AdministeredDose>,
    stats: List<Pair<String, Int>>,
    vaccinationReminders: List<ReminderEntity>,
    validVaccineIds: Set<String>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    availableYears: List<String>,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).verticalScroll(rememberScrollState()).padding(16.dp)) {
        FilterSection(
            availableYears = availableYears.reversed().map { "20$it" },
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onFilterModeChange = onFilterModeChange,
            onQuarterChange = onQuarterChange,
            onMonthChange = onMonthChange
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        SummaryCards(
            doses = doses,
            prevDoses = prevDoses,
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth
        )

        Spacer(modifier = Modifier.height(24.dp))

        VaccinationSectionSelector(
            selected = selectedSection,
            onSelected = { selectedSection = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSection == 0) {
            VaccineStatsSection(stats = stats)
        } else {
            UpcomingVaccineNeedSection(reminders = vaccinationReminders, validVaccineIds = validVaccineIds)
        }
    }
}

@Composable
private fun SummaryCards(
    doses: List<AdministeredDose>,
    prevDoses: List<AdministeredDose>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int
) {
    val customColors = LocalCustomColors.current
    val totalDoses = doses.sumOf { it.quantity }
    val prevTotalDoses = prevDoses.sumOf { it.quantity }

    val monthCount = remember(doses, filterMode, fyQuarter, selectedMonth) {
        StatisticsUtils.monthCountForFilter(doses.map { it.dateGiven }, filterMode, fyQuarter, selectedMonth)
    }
    val avg = if (monthCount == 0) 0.0 else totalDoses.toDouble() / monthCount

    // "Overall" has no meaningful previous period to compare against (its own previous
    // period resolves back to itself), so any growth% would be artificial - omit it rather
    // than show a misleading 0%.
    val growth = if (filterMode == "Overall") null else StatisticsUtils.calculateGrowth(totalDoses.toDouble(), prevTotalDoses.toDouble())

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryCard(
            modifier = Modifier.weight(1f),
            title = "Total Doses",
            value = totalDoses.toString(),
            icon = Icons.Default.FactCheck,
            iconColor = customColors.textGreen,
            iconBackground = customColors.softGreen,
            growthPercentage = growth
        )
        SummaryCard(
            modifier = Modifier.weight(1f),
            title = "Avg / Month",
            value = String.format(Locale.US, "%.1f", avg),
            icon = Icons.Default.Timeline,
            iconColor = customColors.textBlue,
            iconBackground = customColors.softBlue
        )
    }
}

@Composable
private fun VaccineStatsSection(stats: List<Pair<String, Int>>) {
    Text("All Administered Vaccines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(16.dp))

    if (stats.isEmpty()) {
        Text("No vaccinations in this period", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val maxCount = remember(stats) { stats.firstOrNull()?.second ?: 1 }
    stats.forEach { (name, count) ->
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text("$count", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { count.toFloat() / maxCount },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
            label = { Text("Administered") },
            modifier = Modifier.weight(1f)
        )
        SegmentedButton(
            selected = selected == 1,
            onClick = { onSelected(1) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text("Upcoming") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UpcomingVaccineNeedSection(reminders: List<ReminderEntity>, validVaccineIds: Set<String>) {
    val stats = remember(reminders, validVaccineIds) {
        calculateUpcomingVaccineNeeds(reminders, validVaccineIds)
    }
    var expandedType by rememberSaveable { mutableStateOf<String?>(null) }

    Text(
        "Upcoming Vaccine Need",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(12.dp))

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
                .padding(vertical = 6.dp)
                .clickable {
                    expandedType = if (expanded) null else stat.type
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
        val currentFY = StatisticsUtils.displayFilterMode(filterMode)
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

private data class UpcomingVaccineTypeStat(
    val type: String,
    val count: Int,
    val brands: List<Pair<String, Int>>
)

private fun calculateUpcomingVaccineNeeds(
    reminders: List<ReminderEntity>,
    validVaccineIds: Set<String>
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
                val names = reminder.vaccineName.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val ids = reminder.nxtVaccineId
                // Where IDs were recorded alongside the names (index-aligned, per the same
                // distinct-list convention used when saving these reminders - see
                // ReminderRepositoryImpl.saveNextVaccination), only count a name whose
                // nxt_vaccine_id is verified against the vaccine table. Legacy rows with no
                // IDs recorded at all fall back to the names as-is.
                val verifiedNames = if (ids.isNullOrEmpty()) {
                    names
                } else {
                    names.filterIndexed { index, _ -> ids.getOrNull(index) in validVaccineIds }
                }
                verifiedNames
                    .map { PatientUtils.cleanVaccineName(it) }
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

private fun calculateVaccineStats(doses: List<AdministeredDose>): List<Pair<String, Int>> {
    val vaccineCounts = mutableMapOf<String, Int>()
    doses.forEach { dose ->
        vaccineCounts[dose.vaccineName] = (vaccineCounts[dose.vaccineName] ?: 0) + dose.quantity
    }
    return vaccineCounts.toList().sortedByDescending { it.second }
}
