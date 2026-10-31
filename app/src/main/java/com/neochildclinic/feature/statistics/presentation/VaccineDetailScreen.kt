package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.common.DateCategory
import com.neochildclinic.core.common.DateClassifier
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.SkeletonList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineDetailScreen(
    type: String,
    vaccineId: String?,
    onBack: () -> Unit,
    onPatientClick: (String) -> Unit,
    viewModel: VaccineDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Brand name is resolved from the catalog by id, so it may still be loading (or absent
    // for a catalog row since removed) - fall back to the vaccine type alone rather than
    // showing a blank or an id.
    val header = uiState.brandName?.let { "$type \u2014 $it" } ?: type

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = {
                        Text(
                            text = header,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
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
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                when {
                    uiState.isLoading -> {
                        SkeletonList(
                            modifier = Modifier.fillMaxSize(),
                            count = 8,
                            cardShaped = true,
                            spacing = Spacing.sm,
                            contentPadding = PaddingValues(Spacing.screen)
                        )
                    }

                    uiState.entries.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.screen),
                            contentAlignment = Alignment.Center
                        ) {
                            StatisticsEmptyState(
                                icon = Icons.Default.Vaccines,
                                title = "No patients due",
                                message = "No patients are currently waiting for this vaccine."
                            )
                        }
                    }

                    else -> {
                        val patientCount = remember(uiState.entries) {
                            uiState.entries.mapNotNull { it.patient?.id }.distinct().size
                        }
                        val overdueCount = remember(uiState.entries) {
                            uiState.entries
                                .filter { DateClassifier.classify(it.reminder.dueDate) is DateCategory.Overdue }
                                .map { it.reminder.id }
                                .distinct()
                                .size
                        }

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
                                VaccineDueSummary(
                                    header = header,
                                    patientCount = patientCount,
                                    overdueCount = overdueCount
                                )
                            }

                            item {
                                StatisticsSectionHeader(
                                    title = "Patients",
                                    subtitle = "${uiState.entries.size} vaccine entries \u00b7 sorted by due date"
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                            }

                            items(uiState.entries, key = { "${it.reminder.id}_${it.brandName}" }) { entry ->
                                VaccinePatientCard(
                                    entry = entry,
                                    type = type,
                                    isBrandSpecific = uiState.brandName != null,
                                    onClick = {
                                        entry.patient?.id?.let { onPatientClick(it) }
                                    }
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
private fun VaccineDueSummary(header: String, patientCount: Int, overdueCount: Int) {
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
                    imageVector = Icons.Default.Vaccines,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("$patientCount patients due")
                        if (overdueCount > 0) append(" \u00b7 $overdueCount overdue")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun VaccinePatientCard(
    entry: VaccineDetailEntry,
    type: String,
    isBrandSpecific: Boolean,
    onClick: () -> Unit
) {
    val customColors = LocalCustomColors.current
    val patient = entry.patient
    val age = remember(patient?.dob) { patient?.dob?.let { PatientUtils.formatAgeYearsMonths(it) } }
    val category = remember(entry.reminder.dueDate) { DateClassifier.classify(entry.reminder.dueDate) }
    val overdue = category is DateCategory.Overdue
    val today = category is DateCategory.Today
    val dueContainer = when {
        overdue -> MaterialTheme.colorScheme.errorContainer
        today -> customColors.warningContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val dueContent = when {
        overdue -> MaterialTheme.colorScheme.onErrorContainer
        today -> customColors.textWarning
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val dueText = when (category) {
        is DateCategory.Overdue -> "Overdue by ${category.days} day${if (category.days == 1) "" else "s"}"
        DateCategory.Today -> "Due today"
        DateCategory.Tomorrow -> "Due tomorrow"
        is DateCategory.Future -> "Upcoming"
    }

    val tagText = if (isBrandSpecific) {
        entry.brandName
    } else {
        if (entry.brandName.isNotBlank() && !entry.brandName.equals(type, ignoreCase = true)) {
            "$type • ${entry.brandName}"
        } else {
            type
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = patient != null, onClick = onClick),
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
            PatientAvatar(name = patient?.name ?: "Unknown Patient")
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patient?.name ?: "Unknown patient",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        "Due ${PatientUtils.formatDateForDisplay(entry.reminder.dueDate)}",
                        age?.let { "$it old" },
                        patient?.gender?.takeIf { it.isNotBlank() }
                    ).joinToString("  \u00b7  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = tagText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = dueContainer
                    ) {
                        Text(
                            text = dueText,
                            style = MaterialTheme.typography.labelSmall,
                            color = dueContent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                            maxLines = 1
                        )
                    }
                }
            }
            if (patient != null) {
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
}
