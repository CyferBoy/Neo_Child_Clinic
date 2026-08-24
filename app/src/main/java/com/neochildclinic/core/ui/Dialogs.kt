package com.neochildclinic.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neochildclinic.data.local.entity.AuditLogEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Standardized Delete Confirmation Dialog to maintain UI consistency across the app.
 */
@Composable
fun DeleteConfirmationDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String = "Delete Confirmation",
    message: String = "Are you sure you want to delete this item? This action cannot be undone."
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dialog to display a list of audit logs for a patient or entity.
 */
@Composable
fun AuditLogDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    logs: List<AuditLogEntity>
) {
    if (show) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Audit History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (logs.isEmpty()) {
                        Text(
                            text = "No history recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(logs) { log ->
                                AuditLogItem(log)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogItem(log: AuditLogEntity) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()) }
    val timeString = remember(log.timestamp) { 
        val date = com.neochildclinic.core.utils.PatientUtils.parseDate(log.timestamp) ?: Date(0)
        dateFormat.format(date)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = log.action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Text(
            text = "by ${log.user}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )

        if (!log.remarks.isNullOrBlank()) {
            Text(
                text = log.remarks,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
