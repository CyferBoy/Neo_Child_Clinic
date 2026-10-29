package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.feature.statistics.domain.StatisticsUtils
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestonePatientsScreen(
    milestoneKey: String,
    onBack: () -> Unit,
    onPatientClick: (String) -> Unit,
    viewModel: MilestonePatientsViewModel = hiltViewModel()
) {
    val patients by viewModel.patients.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val label = remember(milestoneKey) { StatisticsUtils.milestoneLabelForKey(milestoneKey) ?: "" }

    val today = remember { Calendar.getInstance() }
    val windowEnd = remember { Calendar.getInstance().apply { add(Calendar.MONTH, 2) } }

    // Same one-patient-one-milestone rule as the summary cards (PatientsTab), so the list
    // shown here always matches the count on the card that opened it. "Older" has no
    // milestone date from getNextAgeMilestone() (it only looks ahead) - those patients are
    // identified the same way the summary card counts them, and sorted oldest DOB first as
    // the natural analog of "earliest milestone date first".
    val entries = remember(patients, label, milestoneKey) {
        val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
        if (milestoneKey == "older") {
            patients
                .filter { PatientUtils.getNextAgeMilestone(it.dob, today, windowEnd) == null && PatientUtils.isOlderThanMonths(it.dob, 19, today) }
                .mapNotNull { patient ->
                    val birthMillis = PatientUtils.parseDate(patient.dob)?.time ?: return@mapNotNull null
                    Quadruple(patient, birthMillis, "Older", null)
                }
                .sortedBy { it.second }
        } else {
            patients.mapNotNull { patient ->
                val milestone = PatientUtils.getNextAgeMilestone(patient.dob, today, windowEnd) ?: return@mapNotNull null
                if (milestone.label != label) return@mapNotNull null
                Quadruple(patient, milestone.date.timeInMillis, milestone.label, milestone.date.toInstant().atZone(java.time.ZoneId.systemDefault()).format(dateFormat))
            }.sortedBy { it.second }
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("$label milestone", fontWeight = FontWeight.SemiBold) },
                    onBack = onBack,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                when {
                    isLoading -> {
                        SkeletonList(
                            modifier = Modifier.fillMaxSize(),
                            count = 8,
                            cardShaped = true,
                            spacing = Spacing.sm,
                            contentPadding = PaddingValues(Spacing.screen)
                        )
                    }

                    entries.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.screen),
                            contentAlignment = Alignment.Center
                        ) {
                            StatisticsEmptyState(
                                icon = Icons.Default.CalendarMonth,
                                title = "No patients",
                                message = if (milestoneKey == "older") {
                                    "No patients are older than 19 months."
                                } else {
                                    "No patients are reaching this milestone in the next 2 months."
                                }
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Spacing.screen,
                                end = Spacing.screen,
                                top = Spacing.lg,
                                bottom = Spacing.xxl
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            item {
                                MilestoneSummaryBanner(
                                    label = label,
                                    count = entries.size,
                                    older = milestoneKey == "older"
                                )
                            }

                            item {
                                StatisticsSectionHeader(
                                    title = "Patients",
                                    subtitle = "${entries.size} in this milestone"
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                            }

                            items(entries, key = { it.first.id }) { (patient, _, milestoneLabel, milestoneDate) ->
                                MilestonePatientCard(
                                    patient = patient,
                                    milestoneLabel = milestoneLabel,
                                    milestoneDate = milestoneDate,
                                    onClick = { onPatientClick(patient.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneSummaryBanner(label: String, count: Int, older: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ChartContainerShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$label milestone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (older) {
                        "$count patients older than 19 months"
                    } else {
                        "$count patients reaching this milestone in the next 2 months"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun MilestonePatientCard(
    patient: com.neochildclinic.domain.model.Patient,
    milestoneLabel: String,
    milestoneDate: String?,
    onClick: () -> Unit
) {
    val customColors = LocalCustomColors.current
    val age = remember(patient.dob) { PatientUtils.formatAgeYearsMonths(patient.dob) }
    val dob = remember(patient.dob) { PatientUtils.formatDateForDisplay(patient.dob) }
    val milestoneColor = if (milestoneDate == null) {
        customColors.textOrange
    } else {
        MaterialTheme.colorScheme.primary
    }
    val milestoneContainer = if (milestoneDate == null) {
        customColors.softOrange
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = StatTileShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.card),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PatientAvatar(name = patient.name)
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patient.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        "DOB $dob",
                        age?.let { "$it old" }
                    ).joinToString("  \u00b7  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = milestoneContainer
                ) {
                    Text(
                        text = if (milestoneDate != null) {
                            "$milestoneLabel \u00b7 $milestoneDate"
                        } else {
                            "$milestoneLabel \u00b7 older than 19 months"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = milestoneColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
