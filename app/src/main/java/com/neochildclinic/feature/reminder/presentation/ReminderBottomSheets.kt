package com.neochildclinic.feature.reminder.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDueBottomSheet(
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onDismissReminder: () -> Unit,
    onReschedule: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xxl)
        ) {
            Text(
                text = "Manage Due Vaccination",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(Spacing.lg)
            )
            
            ListItem(
                headlineContent = { Text("Completed") },
                leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LocalCustomColors.current.textGreen) },
                modifier = Modifier.clickable { onComplete() }
            )
            ListItem(
                headlineContent = { Text("Dismissed") },
                leadingContent = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                modifier = Modifier.clickable { onDismissReminder() }
            )
            ListItem(
                headlineContent = { Text("Reschedule") },
                leadingContent = { Icon(Icons.Default.Event, contentDescription = null) },
                modifier = Modifier.clickable { onReschedule() }
            )

            ListItem(
                headlineContent = { Text("Cancel") },
                leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}
