package com.neochildclinic.features.personalreminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.DeleteConfirmationDialog
import com.neochildclinic.core.utils.PatientUtils
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.PersonalReminderStatus
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalReminderDetailsSheet(
    reminder: PersonalReminderEntity,
    patient: Patient?,
    vaccineLabel: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onPatientClick: ((String) -> Unit)?,
    onMarkReady: () -> Unit,
    onMarkPending: () -> Unit,
    onMarkCompleted: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val status = PersonalReminderStatus.fromRaw(reminder.status)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = vaccineLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(status)
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(
                label = "Patient",
                value = patient?.name ?: "Unknown patient",
                onClick = if (patient != null && onPatientClick != null) {
                    { onPatientClick(patient.id) }
                } else null
            )
            DetailRow(label = "Reminder Date", value = PatientUtils.formatDateForDisplay(reminder.reminderDate))

            if (!reminder.note.isNullOrBlank()) {
                DetailRow(label = "Requirement / Note", value = reminder.note)
            }

            DetailRow(
                label = "Advance Received",
                value = if (reminder.advanceReceived) "Yes" else "No"
            )
            if (reminder.advanceReceived) {
                reminder.advanceAmount?.let {
                    DetailRow(label = "Advance Amount", value = String.format(Locale.US, "\u20b9%,.0f", it))
                }
                reminder.advanceDate?.let {
                    DetailRow(label = "Advance Date", value = PatientUtils.formatDateForDisplay(it))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            DetailRow(label = "Created", value = PatientUtils.formatDateTimeForDisplay(reminder.createdAt))
            DetailRow(label = "Last Updated", value = PatientUtils.formatDateTimeForDisplay(reminder.updatedAt))
            reminder.completedAt?.let {
                DetailRow(label = "Completed", value = PatientUtils.formatDateTimeForDisplay(it))
            }
            reminder.cancelledAt?.let {
                DetailRow(label = "Cancelled", value = PatientUtils.formatDateTimeForDisplay(it))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Only show actions that make sense for the current status - a Completed
            // or Cancelled reminder is a historical record and can only be deleted.
            when (status) {
                PersonalReminderStatus.PENDING, PersonalReminderStatus.READY -> {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (status == PersonalReminderStatus.PENDING) {
                        OutlinedButton(onClick = onMarkReady, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Mark as Ready")
                        }
                    } else {
                        OutlinedButton(onClick = onMarkPending, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Move back to Pending")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showCompleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mark as Completed")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showCancelConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel Requirement")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                PersonalReminderStatus.COMPLETED, PersonalReminderStatus.CANCELLED -> {
                    // Historical record - nothing to change except deleting it entirely.
                }
            }

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete")
            }
        }
    }

    DeleteConfirmationDialog(
        show = showDeleteConfirm,
        onDismiss = { showDeleteConfirm = false },
        onConfirm = {
            showDeleteConfirm = false
            onDelete()
        },
        title = "Delete Reminder",
        message = "Delete this personal vaccine reminder? This only removes the reminder itself - it will not affect the patient's vaccination, consultation, payment, or financial records."
    )

    if (showCompleteConfirm) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirm = false },
            title = { Text("Mark as Completed") },
            text = { Text("Confirm that this vaccine requirement has been fully dealt with. This cannot be undone automatically.") },
            confirmButton = {
                TextButton(onClick = { showCompleteConfirm = false; onMarkCompleted() }) { Text("Mark Completed") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Cancel Requirement") },
            text = { Text("Mark this personal reminder as no longer needed? It will move to the Cancelled tab and remain accessible there.") },
            confirmButton = {
                TextButton(onClick = { showCancelConfirm = false; onCancel() }) {
                    Text("Cancel Requirement", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)
    } else {
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
    }
    Column(modifier = rowModifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
            fontWeight = if (onClick != null) FontWeight.Medium else FontWeight.Normal
        )
    }
}
