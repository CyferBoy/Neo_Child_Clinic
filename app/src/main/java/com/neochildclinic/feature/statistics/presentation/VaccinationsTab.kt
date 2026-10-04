package com.neochildclinic.feature.statistics.presentation
import com.neochildclinic.feature.statistics.domain.StatisticsUtils

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
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.common.PatientUtils
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
fun VaccinationsTab(vaccinations: List<Vaccination>, vaccinationReminders: List<Reminder> = emptyList(), vaccines: List<InventoryItem> = emptyList(), onVaccineTypeClick: (String, String?) -> Unit = { _, _ -> }) {
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
        onFilterModeChange = { filterMode = it; fyQuarter = 0; selectedMonth = -1 },
        onQuarterChange = { if (filterMode != "Overall") { fyQuarter = if (fyQuarter == it) 0 else it; selectedMonth = -1 } },
        onMonthChange = { if (fyQuarter != 0 && filterMode != "Overall") { selectedMonth = if (selectedMonth == it) -1 else it } },
        onVaccineTypeClick = onVaccineTypeClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccinationsContent(
    doses: List<AdministeredDose>,
    prevDoses: List<AdministeredDose>,
    stats: List<Pair<String, Int>>,
    vaccinationReminders: List<Reminder>,
    validVaccineIds: Set<String>,
    filterMode: String,
    fyQuarter: Int,
    selectedMonth: Int,
    availableYears: List<String>,
    onFilterModeChange: (String) -> Unit,
    onQuarterChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onVaccineTypeClick: (String, String?) -> Unit = { _, _ -> }
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).verticalScroll(rememberScrollState()).padding(Spacing.lg)) {
        FilterSection(
            availableYears = availableYears.reversed().map { "20$it" },
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onFilterModeChange = onFilterModeChange,
            onQuarterChange = onQuarterChange,
            onMonthChange = onMonthChange,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Text("Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(Spacing.lg))

        SummaryCards(
            doses = doses,
            prevDoses = prevDoses,
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        VaccinationSectionSelector(
            selected = selectedSection,
            onSelected = { selectedSection = it }
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        if (selectedSection == 0) {
            VaccineStatsSection(stats = stats)
        } else {
            UpcomingVaccineNeedSection(reminders = vaccinationReminders, validVaccineIds = validVaccineIds, onVaccineTypeClick = onVaccineTypeClick)
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

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
    Spacer(modifier = Modifier.height(Spacing.lg))

    if (stats.isEmpty()) {
        Text("No vaccinations in this period", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val maxCount = remember(stats) { stats.firstOrNull()?.second ?: 1 }
    stats.forEach { (name, count) ->
        Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text("$count", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
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
private fun UpcomingVaccineNeedSection(reminders: List<Reminder>, validVaccineIds: Set<String>, onVaccineTypeClick: (String, String?) -> Unit = { _, _ -> }) {
    val stats = remember(reminders, validVaccineIds) {
        calculateUpcomingVaccineNeeds(reminders, validVaccineIds)
    }
    var expandedType by rememberSaveable { mutableStateOf<String?>(null) }

    Text(
        "Upcoming Vaccine Need",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(Spacing.md))

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
                .padding(vertical = Spacing.sm),
            shape = StatTileShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stat.type,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            // A type tap always opens the drill-down. It used to navigate
                            // only when the type had exactly one brand and merely expanded
                            // otherwise, so the same gesture meant two different things.
                            onVaccineTypeClick(stat.type, null)
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stat.count.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable {
                                expandedType = if (expanded) null else stat.type
                            }
                        )
                    }
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    stat.brands.forEach { brand ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs)
                                .clickable { onVaccineTypeClick(stat.type, brand.vaccineId) },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                brand.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                brand.count.toString(),
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

internal data class UpcomingVaccineTypeStat(
    val type: String,
    val count: Int,
    val brands: List<UpcomingVaccineBrandStat>
)

/**
 * One brand (vaccine catalog entry) inside a type. Carries the catalog [vaccineId] because
 * that is the stable identity used by the drill-down query - the display [name] is free text
 * typed into the inventory screen and two catalog entries can share one, which would merge
 * unrelated vaccines into a single card. [name] is display-only.
 */
data class UpcomingVaccineBrandStat(
    val vaccineId: String,
    val name: String,
    val count: Int
)

internal fun calculateUpcomingVaccineNeeds(
    reminders: List<Reminder>,
    validVaccineIds: Set<String>
): List<UpcomingVaccineTypeStat> {
    // `reminders` arrives already filtered by DueReminderDao.getUpcomingVaccinations()
    // (ACTIVE + reminderEnabled + category=VACCINATION + is_deleted=0), so this only has
    // to group - it must not re-implement the upcoming rules or the counts would drift
    // from the drill-down. Legacy rows whose type is blank group under "Other", matching
    // the exact string the by-type drill-down query is called with.
    return reminders
        .groupBy { it.type.trim().ifBlank { "Other" } }
        .map { (type, typeReminders) ->
            // Keyed by catalog id so the count and the drill-down query address the same
            // vaccine. Rows with no recorded id (legacy data) cannot be drilled into, so
            // they are counted on the type card only and excluded from the brand list -
            // the same exclusion the old names/ids index-alignment produced.
            val brandCounts = mutableMapOf<String, Pair<String, Int>>()
            typeReminders.forEach { reminder ->
                val names = reminder.vaccineName.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val ids = reminder.nxtVaccineId
                // Where IDs were recorded alongside the names (index-aligned, per the same
                // distinct-list convention used when saving these reminders - see
                // ReminderRepository.saveNextVaccination), only count a name whose
                // nxt_vaccine_id is verified against the vaccine table. Legacy rows with no
                // IDs recorded at all fall back to the names as-is.
                val verified = if (ids.isNullOrEmpty()) {
                    names.mapIndexed { index, name -> (null to name) }
                } else {
                    names.mapIndexedNotNull { index, name ->
                        val id = ids.getOrNull(index)
                        if (id in validVaccineIds) id to name else null
                    }
                }
                verified
                    .map { (id, name) -> id to PatientUtils.cleanVaccineName(name) }
                    .filter { it.second.isNotBlank() }
                    .distinctBy { it.first ?: it.second }
                    .forEach { (id, name) ->
                        if (id != null) {
                            val current = brandCounts[id]
                            brandCounts[id] = name to ((current?.second ?: 0) + 1)
                        }
                    }
            }

            UpcomingVaccineTypeStat(
                type = type,
                count = typeReminders.size,
                brands = brandCounts
                    .map { (id, nameCount) -> UpcomingVaccineBrandStat(id, nameCount.first, nameCount.second) }
                    .sortedByDescending { it.count }
            )
        }
        .sortedByDescending { it.count }
}

private fun calculateVaccineStats(doses: List<AdministeredDose>): List<Pair<String, Int>> =
    doses.groupingBy { it.vaccineName }.fold(0) { acc, dose -> acc + dose.quantity }
        .entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }
