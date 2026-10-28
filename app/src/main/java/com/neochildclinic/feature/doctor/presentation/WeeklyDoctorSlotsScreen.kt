package com.neochildclinic.feature.doctor.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.designsystem.Spacing
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.DeleteConfirmationDialog
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyDoctorSlotsScreen(
    onBack: () -> Unit,
    viewModel: WeeklyDoctorSlotsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddExceptionDialog by remember { mutableStateOf(false) }
    var exceptionToDelete by remember { mutableStateOf<DoctorSlotException?>(null) }

    ShowSnackbar(uiState.error, snackbarHostState) { viewModel.clearError() }

    AppBackground {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = {
                        Text(
                            text = "Doctor Timings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
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
                        contentPadding = PaddingValues(Spacing.lg)
                    )
                } else if (uiState.allDoctors.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                        Text(
                            "No doctor accounts found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.lg)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Spacer(Modifier.height(Spacing.xs))

                        // Hero Doctor Card matching doctor_timings_light.svg
                        DoctorHeroCard(
                            doctors = uiState.allDoctors,
                            selectedDoctor = uiState.selectedDoctor,
                            onDoctorSelected = { viewModel.selectDoctor(it) }
                        )

                        // Weekly Slots Section (doctor_timings_light.svg rect y=204, height=312, rx=20)
                        WeeklySlotsTab(
                            uiState = uiState,
                            onAddSlot = { dayOfWeek, start, end -> viewModel.addWeeklySlot(dayOfWeek, start, end) },
                            onRemoveSlot = { viewModel.removeWeeklySlot(it) },
                            onToggleEditMode = { viewModel.setWeeklySlotsEditMode(it) }
                        )

                        // Unavailability Section (doctor_timings_light.svg y=548)
                        UnavailabilityTab(
                            uiState = uiState,
                            onAddException = { showAddExceptionDialog = true },
                            onDeleteException = { exceptionId ->
                                val exception = uiState.exceptions.find { it.id == exceptionId }
                                exceptionToDelete = exception
                            }
                        )

                        // Save weekly slots bottom button (doctor_timings_light.svg y=720, height=48, rx=14)
                        if (uiState.canManageSelectedDoctor) {
                            StandardButton(
                                onClick = {
                                    viewModel.setWeeklySlotsEditMode(false)
                                    viewModel.refresh()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.xs)
                            ) {
                                Text(
                                    text = "Save weekly slots",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
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
private fun DoctorHeroCard(
    doctors: List<Profile>,
    selectedDoctor: Profile?,
    onDoctorSelected: (Profile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val doctorName = selectedDoctor?.displayName ?: "Select Doctor"
    val initials = doctorName.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { "DR" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.card, vertical = Spacing.md)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    text = doctorName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Pediatrician · ${selectedDoctor?.role?.name?.uppercase() ?: "DOCTOR"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            if (doctors.size > 1) {
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(
                            text = "Change",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        doctors.forEach { doc ->
                            DropdownMenuItem(
                                text = { Text(doc.displayName) },
                                onClick = {
                                    onDoctorSelected(doc)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(0) },
                shape = RoundedCornerShape(10.dp),
                color = if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                border = if (selectedTab == 0) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                shadowElevation = if (selectedTab == 0) 1.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Weekly Slots",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(1) },
                shape = RoundedCornerShape(10.dp),
                color = if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                border = if (selectedTab == 1) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                shadowElevation = if (selectedTab == 1) 1.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Unavailability",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
