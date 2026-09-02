package com.neochildclinic.features.personalreminder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.features.reminder.FilterTabRow

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
    var selectedReminder by remember { mutableStateOf<PersonalReminderEntity?>(null) }

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Personal Vaccine Reminders") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (list.isEmpty()) {
                        EmptyState(tab = uiState.selectedTab)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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

@Composable
private fun EmptyState(tab: PersonalReminderTab) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.EventNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when (tab) {
                    PersonalReminderTab.ACTIVE -> "No personal reminders yet.\nTap + to add one."
                    PersonalReminderTab.COMPLETED -> "No completed reminders yet."
                    PersonalReminderTab.CANCELLED -> "No cancelled reminders."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
