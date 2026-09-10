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
import com.neochildclinic.core.ui.DeleteConfirmationDialog

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
    var vaccinationToRestore by remember { mutableStateOf<com.neochildclinic.domain.model.Vaccination?>(null) }

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
                TopAppBar(
                    title = { Text("Completed & Dismissed") },
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
