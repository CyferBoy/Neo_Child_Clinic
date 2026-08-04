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
    val tabs = listOf("Completed", "Dismissed", "Other Establishment")

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
                        val isEmpty = when (selectedTabIndex) {
                            0 -> uiState.completedRecords.isEmpty()
                            1 -> uiState.dismissedRecords.isEmpty()
                            2 -> uiState.otherRecords.isEmpty()
                            else -> true
                        }
                        
                        if (isEmpty) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (selectedTabIndex) {
                                        0 -> "No vaccinations completed today."
                                        1 -> "No reminders dismissed today."
                                        2 -> "No other establishment records found."
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
                                when (selectedTabIndex) {
                                    0 -> {
                                        items(uiState.completedRecords, key = { it.id }) { record ->
                                            val patient = remember(record.patientId, uiState.patients) { 
                                                uiState.patients.find { it.id == record.patientId } 
                                            }
                                            val vaccination = remember(record.patientId, record.originalDueDate, uiState.processedVaccinations) {
                                                uiState.processedVaccinations.find { 
                                                    it.patientId == record.patientId && it.nextDueDate == record.originalDueDate 
                                                }
                                            }
                                            CompletedDueCard(
                                                record = record,
                                                patient = patient,
                                                vaccination = vaccination,
                                                onClick = { onPatientClick(record.patientId) }
                                            )
                                        }
                                    }
                                    1 -> {
                                        items(uiState.dismissedRecords, key = { it.id }) { record ->
                                            val patient = remember(record.patientId, uiState.patients) { 
                                                uiState.patients.find { it.id == record.patientId } 
                                            }
                                            val vaccination = remember(record.patientId, record.originalDueDate, uiState.processedVaccinations) {
                                                uiState.processedVaccinations.find { 
                                                    it.patientId == record.patientId && it.nextDueDate == record.originalDueDate 
                                                }
                                            }
                                            DismissedDueCard(
                                                record = record,
                                                patient = patient,
                                                vaccination = vaccination,
                                                onClick = { onPatientClick(record.patientId) }
                                            )
                                        }
                                    }
                                    2 -> {
                                        items(uiState.otherRecords, key = { it.id }) { record ->
                                            val patient = remember(record.patientId, uiState.patients) { 
                                                uiState.patients.find { it.id == record.patientId } 
                                            }
                                            val vaccination = remember(record.patientId, record.originalDueDate, uiState.processedVaccinations) {
                                                uiState.processedVaccinations.find { 
                                                    it.patientId == record.patientId && it.nextDueDate == record.originalDueDate 
                                                }
                                            }
                                            OtherEstablishmentDueCard(
                                                record = record,
                                                patient = patient,
                                                vaccination = vaccination,
                                                onClick = { onPatientClick(record.patientId) }
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
}
