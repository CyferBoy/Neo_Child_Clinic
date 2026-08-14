package com.neochildclinic.features.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.ReminderStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDueBottomSheet(
    status: ReminderStatus,
    onDismiss: () -> Unit,
    onMarkAsDone: () -> Unit,
    onDismissReminder: () -> Unit,
    onReschedule: () -> Unit,
    onRestore: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            val title = when (status) {
                ReminderStatus.ACTIVE -> "Manage Due Vaccination"
                ReminderStatus.COMPLETED -> "Completed Vaccination"
                ReminderStatus.DISMISSED -> "Dismissed Reminder"
                else -> "Manage Reminder"
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            
            if (status == ReminderStatus.ACTIVE) {
                ListItem(
                    headlineContent = { Text("Mark as Done") },
                    supportingContent = { Text("Given in this clinic today") },
                    leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)) },
                    modifier = Modifier.clickable { onMarkAsDone() }
                )
                ListItem(
                    headlineContent = { Text("Dismiss Reminder") },
                    supportingContent = { Text("Stop reminders for this vaccine") },
                    leadingContent = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                    modifier = Modifier.clickable { onDismissReminder() }
                )
                ListItem(
                    headlineContent = { Text("Reschedule") },
                    supportingContent = { Text("Change the due date") },
                    leadingContent = { Icon(Icons.Default.Event, contentDescription = null) },
                    modifier = Modifier.clickable { onReschedule() }
                )
            } else {
                ListItem(
                    headlineContent = { Text("Restore to Active") },
                    supportingContent = { Text("Move back to due/overdue schedule") },
                    leadingContent = { Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { onRestore() }
                )
            }

            ListItem(
                headlineContent = { Text("Cancel") },
                leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}
