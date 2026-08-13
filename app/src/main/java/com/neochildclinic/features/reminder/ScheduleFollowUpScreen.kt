package com.neochildclinic.features.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.Priority
import com.neochildclinic.core.ui.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFollowUpScreen(
    patientId: String,
    originalVisitId: String,
    initialVaccines: List<String> = emptyList(),
    onBack: () -> Unit,
    viewModel: FollowUpViewModel = hiltViewModel()
) {
    var dueDate by remember { mutableStateOf(SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date())) }
    var selectedVaccines by remember { mutableStateOf(initialVaccines.joinToString(", ")) }
    var notes by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.NORMAL) }
    var reminderEnabled by remember { mutableStateOf(true) }

    val uiState by viewModel.uiState.collectAsState()

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Schedule Next Vaccination") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Fill in the details for the next visit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DateDropdownPicker(
                    label = "Follow-up Date (Required)",
                    currentDate = dueDate,
                    onDateSelected = { dueDate = it }
                )

                StandardTextField(
                    value = selectedVaccines,
                    onValueChange = { selectedVaccines = it },
                    label = "Next Vaccine(s) (Required)",
                    placeholder = "e.g. Polio, DTaP"
                )

                StandardTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes (Optional)",
                    placeholder = "Special instructions..."
                )

                Text("Priority", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Priority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reminder Enabled", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Notify staff when this follow-up is due.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                StandardButton(
                    onClick = {
                        viewModel.scheduleNextVaccination(
                            patientId = patientId,
                            originalVisitId = originalVisitId,
                            type = "Routine",
                            vaccineNames = selectedVaccines.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            dueDate = dueDate,
                            notes = notes,
                            priority = priority.name,
                            reminderEnabled = reminderEnabled,
                            onSuccess = onBack
                        )
                    },
                    isLoading = uiState.isLoading,
                    enabled = selectedVaccines.isNotBlank() && dueDate.isNotBlank()
                ) {
                    Text("Save Next Vaccination")
                }
            }
        }
    }
}
