package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import java.util.Locale

// A single administered dose, sourced from vaccination_items. The parent visit IS status-gated
// (StatisticsUtils.filterValidVaccinations): only COMPLETED/EXTERNAL visits count as
// administered doses - vaccination_items rows on a pending visit are planned, not given.
// dateGiven is carried over from the parent vaccination purely to filter/group by period;
// it is not a vaccination_items column.
private data class AdministeredDose(val vaccineName: String, val quantity: Int, val dateGiven: String)

private fun administeredDoses(vaccinations: List<Vaccination>, validVaccineIds: Set<String>): List<AdministeredDose> =
    StatisticsUtils.filterValidVaccinations(vaccinations).flatMap { v ->
        v.items
            .filter { it.vaccineName.isNotBlank() && it.vaccineId in validVaccineIds }
            .map { AdministeredDose(PatientUtils.cleanVaccineName(it.vaccineName), it.quantity.coerceAtLeast(0), v.dateGiven) }
    }

@Composable
fun VaccinationsTab(
    vaccinations: List<Vaccination>,
    vaccinationReminders: List<Reminder> = emptyList(),
    vaccines: List<InventoryItem> = emptyList(),
    onVaccineTypeClick: (String, String?) -> Unit = { _, _ -> }
) {
    var filterMode by rememberSaveable { mutableStateOf("Overall") }
    var fyQuarter by rememberSaveable { mutableIntStateOf(0) }
    var selectedMonth by rememberSaveable { mutableIntStateOf(-1) }

    val validVaccineIds = remember(vaccines) { vaccines.map { it.id }.toSet() }
    // All administered doses: filterValidVaccinations keeps only COMPLETED/EXTERNAL parent
    // visits, so a pending visit's items never inflate the Administered segment.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
    ) {
        FilterSection(
            availableYears = availableYears.reversed().map { "20$it" },
            filterMode = filterMode,
            fyQuarter = fyQuarter,
            selectedMonth = selectedMonth,
            onFilterModeChange = onFilterModeChange,
            onQuarterChange = onQuarterChange,
            onMonthChange = onMonthChange
        )

        Spacer(modifier = Modifier.height(Spacing.xl))

        StatisticsSectionHeader(
            title = "Vaccination summary",
            subtitle = "Administered doses in the selected period"
        )
        Spacer(modifier = Modifier.height(Spacing.md))

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
            UpcomingVaccineNeedSection(
                reminders = vaccinationReminders,
                validVaccineIds = validVaccineIds,
                onVaccineTypeClick = onVaccineTypeClick
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xxl))
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

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        SummaryCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            title = "Total Doses",
            value = totalDoses.toString(),
            accentColor = customColors.textSuccess,
            supportingText = "doses administered",
            growthPercentage = growth
        )
        SummaryCard(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            title = "Avg / Month",
            value = String.format(Locale.US, "%.1f", avg),
            accentColor = MaterialTheme.colorScheme.primary,
            supportingText = "doses per month"
        )
    }
}

@Composable
private fun VaccineStatsSection(stats: List<Pair<String, Int>>) {
    StatisticsSectionHeader(
        title = "Administered vaccines",
        subtitle = if (stats.isEmpty()) "No vaccine doses in this period" else "${stats.size} vaccine types"
    )
    Spacer(modifier = Modifier.height(Spacing.md))

    if (stats.isEmpty()) {
        StatisticsEmptyState(
            icon = Icons.Default.Vaccines,
            title = "No vaccinations",
            message = "No administered doses match the selected period."
        )
        return
    }

    val maxCount = remember(stats) { stats.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1 }
    val totalCount = remember(stats) { stats.sumOf { it.second }.coerceAtLeast(1) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        stats.forEach { (name, count) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = StatTileShape,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.card)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${count * 100 / totalCount}% of doses",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / maxCount },
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
private fun UpcomingVaccineNeedSection(
    reminders: List<Reminder>,
    validVaccineIds: Set<String>,
    onVaccineTypeClick: (String, String?) -> Unit = { _, _ -> }
) {
    val stats = remember(reminders, validVaccineIds) {
        calculateUpcomingVaccineNeeds(reminders, validVaccineIds)
    }
    var expandedType by rememberSaveable { mutableStateOf<String?>(null) }

    StatisticsSectionHeader(
        title = "Upcoming vaccine need",
        subtitle = if (stats.isEmpty()) "No upcoming vaccine needs" else "${stats.size} vaccine types due"
    )
    Spacer(modifier = Modifier.height(Spacing.md))

    if (stats.isEmpty()) {
        StatisticsEmptyState(
            icon = Icons.Default.Vaccines,
            title = "Nothing due",
            message = "No upcoming vaccine needs were found."
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        stats.forEach { stat ->
            val expanded = expandedType == stat.type
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = StatTileShape,
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.card)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vaccines,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    // A type tap always opens the drill-down. It used to navigate
                                    // only when the type had exactly one brand and merely expanded
                                    // otherwise, so the same gesture meant two different things.
                                    onVaccineTypeClick(stat.type, null)
                                }
                        ) {
                            Text(
                                text = stat.type,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${stat.count} patients due",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "View patients",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { expandedType = if (expanded) null else stat.type },
                            modifier = Modifier.size(Spacing.touchTarget)
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse ${stat.type}" else "Expand ${stat.type}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (expanded) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        if (stat.brands.isEmpty()) {
                            Text(
                                text = "No brand breakdown is available for these legacy records.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.sm)
                            )
                        } else {
                            stat.brands.forEach { brand ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable { onVaccineTypeClick(stat.type, brand.vaccineId) }
                                        .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = brand.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = brand.count.toString(),
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
