package com.neochildclinic.features.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedDismissedScreen(
    onBack: () -> Unit,
    onPatientClick: (String) -> Unit,
    viewModel: CompletedDismissedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Completed", "Dismissed")

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Completed & Dismissed") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val visibleRecords = uiState.processedVaccinations.filter {
                            if (selectedTabIndex == 0) it.status == com.neochildclinic.domain.model.ReminderStatus.COMPLETED
                            else it.status == com.neochildclinic.domain.model.ReminderStatus.DISMISSED
                        }
                        val isEmpty = visibleRecords.isEmpty()
                        
                        if (isEmpty) {
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
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(visibleRecords, key = { it.patientId + it.nextDueDate + it.status }) { vaccination ->
                                    val patient = remember(vaccination.patientId, uiState.patients) {
                                        uiState.patients.find { it.id == vaccination.patientId }
                                    }
                                    if (vaccination.status == com.neochildclinic.domain.model.ReminderStatus.COMPLETED) {
                                        CompletedRecordCard(vaccination, patient) { onPatientClick(vaccination.patientId) }
                                    } else {
                                        DismissedRecordCard(vaccination, patient) { onPatientClick(vaccination.patientId) }
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
