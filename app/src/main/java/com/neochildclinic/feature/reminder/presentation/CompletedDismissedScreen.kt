package com.neochildclinic.feature.reminder.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.ui.DeleteConfirmationDialog
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination

enum class CompletedDismissedSort(val label: String) {
    COMPLETION_DATE_DESC("Completion/Dismissed date (Newest first)"),
    COMPLETION_DATE_ASC("Completion/Dismissed date (Oldest first)"),
    GIVEN_DATE_DESC("Given date (Newest first)"),
    GIVEN_DATE_ASC("Given date (Oldest first)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedDismissedScreen(
    onBack: () -> Unit,
    onPatientClick: (String) -> Unit,
    initialTab: Int = 0,
    viewModel: CompletedDismissedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(initialTab) }
    var currentSort by rememberSaveable { mutableStateOf(CompletedDismissedSort.COMPLETION_DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    val tabs = listOf("Completed", "Dismissed")
    var vaccinationToRestore by remember { mutableStateOf<Vaccination?>(null) }

    DeleteConfirmationDialog(
        show = vaccinationToRestore != null,
        onDismiss = { vaccinationToRestore = null },
        onConfirm = {
            vaccinationToRestore?.let { viewModel.restoreReminders(it) }
            vaccinationToRestore = null
        },
        title = "Move to Due",
        message = "Are you sure you want to move this vaccination back to the Due list?"
    )

    AppBackground {
        Scaffold(
            topBar = {
                BackTopAppBar(
                    title = { Text("Completed & Dismissed") },
                    onBack = onBack,
                    actions = {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort records",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                CompletedDismissedSort.entries.forEach { sortOption ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                            ) {
                                                RadioButton(
                                                    selected = currentSort == sortOption,
                                                    onClick = null
                                                )
                                                Text(
                                                    text = sortOption.label,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        },
                                        onClick = {
                                            currentSort = sortOption
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }

                AppPullToRefresh(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.isLoading) {
                        SkeletonList(
                            modifier = Modifier.fillMaxSize(),
                            count = 8,
                            cardShaped = true,
                            spacing = Spacing.sm,
                            contentPadding = PaddingValues(Spacing.lg)
                        )
                    } else {
                        val visibleRecords = uiState.processedVaccinations.filter {
                            if (selectedTabIndex == 0) it.status == ReminderStatus.COMPLETED
                            else it.status == ReminderStatus.DISMISSED
                        }

                        val sortedRecords = remember(visibleRecords, currentSort) {
                            visibleRecords.sortedWith { a, b ->
                                val dateA = when (currentSort) {
                                    CompletedDismissedSort.GIVEN_DATE_DESC, CompletedDismissedSort.GIVEN_DATE_ASC -> {
                                        PatientUtils.parseDate(a.originalDateGiven ?: a.dateGiven)?.time ?: 0L
                                    }
                                    CompletedDismissedSort.COMPLETION_DATE_DESC, CompletedDismissedSort.COMPLETION_DATE_ASC -> {
                                        PatientUtils.parseDate(a.statusDate ?: a.dateGiven)?.time ?: 0L
                                    }
                                }
                                val dateB = when (currentSort) {
                                    CompletedDismissedSort.GIVEN_DATE_DESC, CompletedDismissedSort.GIVEN_DATE_ASC -> {
                                        PatientUtils.parseDate(b.originalDateGiven ?: b.dateGiven)?.time ?: 0L
                                    }
                                    CompletedDismissedSort.COMPLETION_DATE_DESC, CompletedDismissedSort.COMPLETION_DATE_ASC -> {
                                        PatientUtils.parseDate(b.statusDate ?: b.dateGiven)?.time ?: 0L
                                    }
                                }

                                when (currentSort) {
                                    CompletedDismissedSort.GIVEN_DATE_DESC, CompletedDismissedSort.COMPLETION_DATE_DESC -> {
                                        dateB.compareTo(dateA)
                                    }
                                    CompletedDismissedSort.GIVEN_DATE_ASC, CompletedDismissedSort.COMPLETION_DATE_ASC -> {
                                        dateA.compareTo(dateB)
                                    }
                                }
                            }
                        }

                        if (sortedRecords.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (selectedTabIndex) {
                                        0 -> "No completed vaccinations found."
                                        1 -> "No dismissed reminders found."
                                        else -> ""
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(Spacing.lg),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                items(sortedRecords, key = { it.patientId + it.nextDueDate + it.status + it.dateGiven }) { vaccination ->
                                    val patient = remember(vaccination.patientId, uiState.patients) {
                                        uiState.patients.find { it.id == vaccination.patientId }
                                    }
                                    if (vaccination.status == ReminderStatus.COMPLETED) {
                                        CompletedRecordCard(vaccination, patient) { onPatientClick(vaccination.patientId) }
                                    } else {
                                        DismissedRecordCard(
                                            vaccination = vaccination,
                                            patient = patient,
                                            onLongClick = { vaccinationToRestore = vaccination },
                                            onClick = { onPatientClick(vaccination.patientId) }
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
}
