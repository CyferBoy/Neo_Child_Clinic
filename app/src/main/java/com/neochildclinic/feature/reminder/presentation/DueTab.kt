package com.neochildclinic.feature.reminder.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.designsystem.Spacing
import com.neochildclinic.core.ui.EmptyState
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.ReminderStats
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.VaccinationItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DueTab(
    patients: List<Patient>, 
    filteredVaccinations: List<Vaccination>,
    overdueCount: Int,
    stats: ReminderStats,
    initialFilter: String = "Today",
    onFilterChanged: (String) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onComplete: (Vaccination) -> Unit = {},
    onDismissReminder: (Vaccination, String) -> Unit = { _, _ -> },
    onReschedule: (Vaccination, String, String) -> Unit = { _, _, _ -> },
    onNavigateToCompleted: () -> Unit = {},
    onNavigateToDismissed: () -> Unit = {},
    onPatientClick: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val filters = remember { listOf("All", "Overdue", "Today", "Week", "Month") }
    var selectedVaccination by remember { mutableStateOf<Vaccination?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showReschedulePicker by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
        contentPadding = PaddingValues(bottom = 80.dp, top = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 1. Search Bar
        item {
            StandardTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearchQueryChanged(it)
                },
                label = "",
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search due patients or vaccine...",
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }

        // 2. Filter Pills Row
        item {
            FilterTabRow(
                filters = filters,
                selectedFilter = initialFilter,
                onFilterChanged = onFilterChanged
            )
        }

        // 3. Status Summary Cards Row (Overdue, Today, This Week)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // OVERDUE Card
                Surface(
                    modifier = Modifier.weight(1f).height(76.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "OVERDUE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$overdueCount",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // TODAY Card
                Surface(
                    modifier = Modifier.weight(1f).height(76.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "TODAY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${filteredVaccinations.count { com.neochildclinic.core.common.DateClassifier.classify(it.nextDueDate) is com.neochildclinic.core.common.DateCategory.Today }}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // THIS WEEK Card
                Surface(
                    modifier = Modifier.weight(1f).height(76.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "THIS WEEK",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${filteredVaccinations.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // 4. Completed & Dismissed Quick Links
        item {
            CompletedDismissedSummaryCards(
                completedCount = stats.completedToday,
                dismissedCount = stats.dismissedToday,
                onCompletedClick = onNavigateToCompleted,
                onDismissedClick = onNavigateToDismissed
            )
        }

        // 5. Section Header Eyebrow
        if (filteredVaccinations.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs, bottom = Spacing.xxs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val overdueList = filteredVaccinations.filter { com.neochildclinic.core.common.DateClassifier.classify(it.nextDueDate) is com.neochildclinic.core.common.DateCategory.Overdue }
                    val maxOverdueDays = overdueList.mapNotNull { 
                        (com.neochildclinic.core.common.DateClassifier.classify(it.nextDueDate) as? com.neochildclinic.core.common.DateCategory.Overdue)?.days 
                    }.maxOrNull()

                    val headerText = if (initialFilter.equals("Overdue", ignoreCase = true)) {
                        "${filteredVaccinations.size} overdue vaccinations"
                    } else {
                        "${filteredVaccinations.size} vaccinations due"
                    }

                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (maxOverdueDays != null && maxOverdueDays > 0) {
                        Text(
                            text = "Oldest $maxOverdueDays days",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // 6. Vaccine List or Empty State
        if (filteredVaccinations.isEmpty()) {
            item {
                EmptyState(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                    icon = Icons.Default.CheckCircleOutline,
                    title = "Nothing due",
                    message = "No vaccinations are due for the selected filter."
                )
            }
        } else {
            items(
                items = filteredVaccinations, 
                key = { it.patientId + it.nextDueDate + it.nxtVaccineNames.joinToString() }
            ) { v ->
                val patient = remember(v.patientId, patients) { patients.find { it.id == v.patientId } }
                DuePatientCard(
                    vaccination = v, 
                    patient = patient,
                    onLongPress = { 
                        selectedVaccination = v
                        showManageSheet = true 
                    },
                    onClick = { onPatientClick(v.patientId) },
                    modifier = Modifier.animateItem()
                )
            }

            // Footer note matching due_vaccinations_all_light.svg (y=760)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Showing ${filteredVaccinations.size} of ${filteredVaccinations.size} · tap a patient for actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    if (showManageSheet && selectedVaccination != null) {
        ManageDueBottomSheet(
            onDismiss = { showManageSheet = false },
            onComplete = {
                selectedVaccination?.let { onComplete(it) }
                showManageSheet = false 
            },
            onDismissReminder = {
                showManageSheet = false
                showDismissDialog = true
            },
            onReschedule = { 
                showManageSheet = false
                showReschedulePicker = true 
            },
        )
    }

    if (showDismissDialog && selectedVaccination != null) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDismissDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Dismiss Reminder", fontWeight = FontWeight.Bold) },
            text = {
                StandardTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Reason for dismissal",
                    placeholder = "e.g. Taken elsewhere",
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedVaccination?.let { onDismissReminder(it, reason) }
                        showDismissDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Dismiss") }
            },
            dismissButton = {
                TextButton(onClick = { showDismissDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReschedulePicker && selectedVaccination != null) {
        RescheduleDialog(
            onDismiss = { showReschedulePicker = false },
            onConfirm = { newDate, reason ->
                selectedVaccination?.let { onReschedule(it, newDate, reason) }
                showReschedulePicker = false
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DueTabPreview() {
    NeoChildTheme {
        DueTab(
            patients = listOf(Patient("1", "Aarav Sharma", "9876543210", "", "2022-01-01", "Male", "", "")),
            filteredVaccinations = listOf(
                Vaccination(
                    id = "1",
                    patientId = "1",
                    items = listOf(VaccinationItem(id = "1", vaccinationId = "1", vaccineId = "v1", batchId = "", vaccineName = "BCG")),
                    nextVaccinations = listOf(com.neochildclinic.domain.model.NextVaccinationSummary(
                        type = "Booster",
                        vaccineNames = listOf("HepB"),
                        dueDate = "1 Feb 2026"
                    )),
                    dateGiven = "1 Jan 2026",
                    cashAmount = 500.0,
                    onlineAmount = 0.0,
                    totalPaid = 500.0,
                    withFees = false,
                    doctorsAcc = false,
                    status = ReminderStatus.ACTIVE
                )
            ),
            overdueCount = 1,
            stats = ReminderStats(completedToday = 12, dismissedToday = 3),
            onComplete = {},
            onDismissReminder = { _, _ -> },
            onReschedule = { _, _, _ -> },
        )
    }
}
