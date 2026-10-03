package com.neochildclinic.feature.personalreminder.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.EmptyState
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.domain.model.PersonalReminder
import com.neochildclinic.feature.reminder.presentation.FilterTabRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalReminderScreen(
    onBack: () -> Unit,
    onAddReminder: () -> Unit,
    onEditReminder: (String) -> Unit,
    onPatientClick: (String) -> Unit,
    viewModel: PersonalReminderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedReminder by remember { mutableStateOf<PersonalReminder?>(null) }

    AppBackground {
        Scaffold(
            topBar = {
                BackTopAppBar(
                    title = { Text("Personal Vaccine Reminders") },
                    onBack = onBack
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddReminder) {
                    Icon(Icons.Default.Add, contentDescription = "Add Personal Reminder")
                }
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val tabs = listOf(
                        PersonalReminderTab.ACTIVE to "Active",
                        PersonalReminderTab.COMPLETED to "Completed",
                        PersonalReminderTab.CANCELLED to "Cancelled"
                    )
                    FilterTabRow(
                        filters = tabs.map { it.second },
                        selectedFilter = tabs.first { it.first == uiState.selectedTab }.second,
                        onFilterChanged = { label ->
                            tabs.firstOrNull { it.second == label }?.let { viewModel.selectTab(it.first) }
                        }
                    )

                    val list = when (uiState.selectedTab) {
                        PersonalReminderTab.ACTIVE -> uiState.active
                        PersonalReminderTab.COMPLETED -> uiState.completed
                        PersonalReminderTab.CANCELLED -> uiState.cancelled
                    }

                    if (uiState.isLoading) {
                        SkeletonList(
                            modifier = Modifier.fillMaxSize(),
                            count = 6,
                            cardShaped = true,
                            spacing = Spacing.sm,
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md)
                        )
                    } else if (list.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.fillMaxSize(),
                            icon = Icons.AutoMirrored.Filled.EventNote,
                            title = when (uiState.selectedTab) {
                                PersonalReminderTab.ACTIVE -> "No personal reminders yet"
                                PersonalReminderTab.COMPLETED -> "No completed reminders"
                                PersonalReminderTab.CANCELLED -> "No cancelled reminders"
                            },
                            message = if (uiState.selectedTab == PersonalReminderTab.ACTIVE)
                                "Tap + to add a reminder."
                            else null
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                            contentPadding = PaddingValues(top = Spacing.md, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            items(items = list, key = { it.id }) { reminder ->
                                PersonalReminderCard(
                                    reminder = reminder,
                                    patient = reminder.patientId?.let { uiState.patientsById[it] },
                                    onClick = { selectedReminder = reminder }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedReminder?.let { reminder ->
        PersonalReminderDetailsSheet(
            reminder = reminder,
            patient = uiState.patientsById[reminder.patientId],
            vaccineLabel = reminder.vaccineLabel?.takeIf { it.isNotBlank() } ?: "Vaccine Requirement",
            onDismiss = { selectedReminder = null },
            onEdit = {
                selectedReminder = null
                onEditReminder(reminder.id)
            },
            onPatientClick = { patientId ->
                selectedReminder = null
                onPatientClick(patientId)
            },
            onMarkReady = { viewModel.markReady(reminder.id); selectedReminder = null },
            onMarkPending = { viewModel.markPending(reminder.id); selectedReminder = null },
            onMarkCompleted = { viewModel.markCompleted(reminder.id); selectedReminder = null },
            onCancel = { viewModel.cancel(reminder.id); selectedReminder = null },
            onDelete = { viewModel.delete(reminder.id); selectedReminder = null }
        )
    }
}
