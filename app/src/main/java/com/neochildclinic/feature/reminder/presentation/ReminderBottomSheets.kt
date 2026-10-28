package com.neochildclinic.feature.reminder.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xxl)
        ) {
            Text(
                text = "Manage Due Vaccination",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(Spacing.lg)
            )
            
            ListItem(
                headlineContent = { Text("Completed", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                modifier = Modifier.clickable { onComplete() }
            )
            ListItem(
                headlineContent = { Text("Dismissed", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onDismissReminder() }
            )
            ListItem(
                headlineContent = { Text("Reschedule", fontWeight = FontWeight.SemiBold) },
                leadingContent = { Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onReschedule() }
            )

            ListItem(
                headlineContent = { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingContent = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    }
}
