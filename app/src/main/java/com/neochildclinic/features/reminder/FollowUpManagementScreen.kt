package com.neochildclinic.features.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.domain.model.ReminderStatus
import com.neochildclinic.core.ui.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpManagementScreen(
    patientId: String,
    patientName: String,
    onBack: () -> Unit,
    onNavigateToAddVaccination: (String, String) -> Unit,
    viewModel: FollowUpViewModel = hiltViewModel()
) {
    val followUps by viewModel.getPatientFollowUps(patientId).collectAsState(initial = emptyList())
    var selectedFollowUp by remember { mutableStateOf<ReminderEntity?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Follow-ups", style = MaterialTheme.typography.titleMedium)
                            Text(patientName, style = MaterialTheme.typography.bodySmall)
                        }
                    },
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
            if (followUps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("No follow-ups recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val grouped = followUps.groupBy { 
                        when {
                            it.status == ReminderStatus.COMPLETED.name -> "Completed"
                            it.status == ReminderStatus.EXTERNAL.name -> "External"
                            it.status == ReminderStatus.DISMISSED.name -> "Cancelled/Dismissed"
                            else -> "Active Follow-ups"
                        }
                    }

                    grouped.forEach { (section, items) ->
                        item {
                            Text(
                                text = section,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(items) { item ->
                            FollowUpCard(
                                reminder = item,
                                onActionClick = {
                                    selectedFollowUp = item
                                    showActionSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showActionSheet && selectedFollowUp != null) {
        selectedFollowUp?.let { followUp ->
            FollowUpActionBottomSheet(
                reminder = followUp,
                onDismiss = { showActionSheet = false },
                onComplete = { reminder ->
                    onNavigateToAddVaccination(reminder.patientId, reminder.vaccineName)
                    showActionSheet = false 
                },
                onDismissReminder = { viewModel.dismissReminder(it, "Staff dismissed"); showActionSheet = false },
                onRestore = { viewModel.restoreReminder(it); showActionSheet = false },
                onDelete = { viewModel.deleteReminder(it); showActionSheet = false }
            )
        }
    }
}

@Composable
fun FollowUpCard(
    reminder: ReminderEntity,
    onActionClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.vaccineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Due: ${reminder.dueDate}", style = MaterialTheme.typography.bodySmall)
                if (reminder.priority != "NORMAL") {
                    Badge(
                        containerColor = if (reminder.priority == "URGENT") Color.Red else Color(0xFFFFA500),
                        contentColor = Color.White
                    ) {
                        Text(reminder.priority, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
            
            IconButton(onClick = onActionClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpActionBottomSheet(
    reminder: ReminderEntity,
    onDismiss: () -> Unit,
    onComplete: (ReminderEntity) -> Unit,
    onDismissReminder: (ReminderEntity) -> Unit,
    onRestore: (ReminderEntity) -> Unit,
    onDelete: (ReminderEntity) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                "Manage Follow-up",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            
            if (reminder.status != ReminderStatus.COMPLETED.name && reminder.status != ReminderStatus.DISMISSED.name) {
                ListItem(
                    headlineContent = { Text("Mark as Completed") },
                    leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)) },
                    modifier = Modifier.clickable { onComplete(reminder) }
                )
                ListItem(
                    headlineContent = { Text("Dismiss Reminder") },
                    leadingContent = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                    modifier = Modifier.clickable { onDismissReminder(reminder) }
                )
            } else {
                ListItem(
                    headlineContent = { Text("Restore to Active") },
                    leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    modifier = Modifier.clickable { onRestore(reminder) }
                )
            }
            
            // Delete should be restricted but here for simplicity
            ListItem(
                headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onDelete(reminder) }
            )
        }
    }
}
