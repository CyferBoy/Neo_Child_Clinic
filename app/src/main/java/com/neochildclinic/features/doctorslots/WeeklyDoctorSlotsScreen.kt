package com.neochildclinic.features.doctorslots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.MessageEffect
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.DoctorDropdown
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.ui.DeleteConfirmationDialog
import com.neochildclinic.domain.model.DoctorSlotException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDoctorSlotsScreen(
    onBack: () -> Unit,
    viewModel: WeeklyDoctorSlotsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddExceptionDialog by remember { mutableStateOf(false) }
    var exceptionToDelete by remember { mutableStateOf<DoctorSlotException?>(null) }

    MessageEffect(uiState.error) { viewModel.clearError() }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("Doctor Timings") },
                    onBack = onBack
                )
            }
        ) { paddingValues ->
            val isRefreshing by viewModel.isRefreshing.collectAsState()
            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 6,
                        cardShaped = true,
                        contentPadding = PaddingValues(16.dp)
                    )
                } else if (uiState.allDoctors.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No doctor accounts found.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))

                        DoctorDropdown(
                            doctors = uiState.allDoctors,
                            selectedDoctor = uiState.selectedDoctor,
                            onDoctorSelected = { viewModel.selectDoctor(it) }
                        )

                        SegmentedControl(
                            selectedTab = uiState.selectedTab,
                            onTabSelected = { viewModel.selectTab(it) }
                        )

                        when (uiState.selectedTab) {
                            0 -> WeeklySlotsTab(
                                uiState = uiState,
                                onAddSlot = { dayOfWeek, start, end -> viewModel.addWeeklySlot(dayOfWeek, start, end) },
                                onRemoveSlot = { viewModel.removeWeeklySlot(it) },
                                onToggleEditMode = { viewModel.setWeeklySlotsEditMode(it) }
                            )
                            1 -> UnavailabilityTab(
                                uiState = uiState,
                                onAddException = { showAddExceptionDialog = true },
                                onDeleteException = { viewModel.deleteException(it) }
                            )
                        }

                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showAddExceptionDialog) {
        AddExceptionDialog(
            onDismiss = { showAddExceptionDialog = false },
            onAddFullDay = { date, reason ->
                viewModel.addFullDayException(date, reason)
                showAddExceptionDialog = false
            },
            onAddCustomTime = { date, start, end, reason ->
                viewModel.addCustomTimeException(date, start, end, reason)
                showAddExceptionDialog = false
            }
        )
    }

    DeleteConfirmationDialog(
        show = exceptionToDelete != null,
        onDismiss = { exceptionToDelete = null },
        onConfirm = {
            exceptionToDelete?.let { viewModel.deleteException(it.id) }
            exceptionToDelete = null
        },
        title = "Delete Exception",
        message = "Are you sure you want to delete this date exception?"
    )
}

@Composable
private fun SegmentedControl(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            onClick = { onTabSelected(0) },
            selected = selectedTab == 0,
            label = { Text("Weekly Slots") }
        )
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            onClick = { onTabSelected(1) },
            selected = selectedTab == 1,
            label = { Text("Unavailability") }
        )
    }
}