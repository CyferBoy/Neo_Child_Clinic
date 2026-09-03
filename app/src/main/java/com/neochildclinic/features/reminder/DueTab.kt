package com.neochildclinic.features.reminder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.VaccinationItem
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.domain.repository.ReminderStats
import com.neochildclinic.core.designsystem.NeoChildTheme

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
    onReschedule: (Vaccination, String, String, String) -> Unit = { _, _, _, _ -> },
    onNavigateToCompletedDismissed: () -> Unit = {},
    onPatientClick: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val filters = remember { listOf("All", "Overdue", "Today", "Week", "Month") }
    var selectedVaccination by remember { mutableStateOf<Vaccination?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showReschedulePicker by remember { mutableStateOf(false) }
    var showDismissDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp)
    ) {
        item {
            CompletedDismissedSummaryCards(
                onClick = onNavigateToCompletedDismissed
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    onSearchQueryChanged(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name or phone...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            FilterTabRow(
                filters = filters,
                selectedFilter = initialFilter,
                onFilterChanged = onFilterChanged
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (filteredVaccinations.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No vaccinations due.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                Spacer(modifier = Modifier.height(12.dp))
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
            title = { Text("Dismiss Reminder") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for dismissal") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    selectedVaccination?.let { onDismissReminder(it, reason) }
                    showDismissDialog = false
                }) { Text("Dismiss") }
            },
            dismissButton = {
                TextButton(onClick = { showDismissDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReschedulePicker && selectedVaccination != null) {
        RescheduleDialog(
            onDismiss = { showReschedulePicker = false },
            onConfirm = { newDate, reminderDate, reason ->
                selectedVaccination?.let { onReschedule(it, newDate, reminderDate, reason) }
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
            patients = listOf(Patient("1", "John Doe", "1234567890", "", "2020-01-01", "Male", "", "")),
            filteredVaccinations = listOf(
                Vaccination(
                    id = "1",
                    patientId = "1",
                    items = listOf(VaccinationItem(vaccineName = "BCG")),
                    nextVaccinations = listOf(com.neochildclinic.domain.model.NextVaccinationSummary(
                        type = "Booster",
                        vaccineNames = listOf("HepB"),
                        dueDate = "1 Feb 2024"
                    )),
                    dateGiven = "1 Jan 2024",
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
            onReschedule = { _, _, _, _ -> },
        )
    }
}
