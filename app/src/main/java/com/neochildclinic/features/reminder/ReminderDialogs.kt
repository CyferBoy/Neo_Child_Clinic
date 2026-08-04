package com.neochildclinic.features.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.DateDropdownPicker
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RescheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    val sdf = remember { SimpleDateFormat("d MMM yyyy", Locale.ENGLISH) }
    val today = remember { sdf.format(Date()) }
    
    var newDueDate by remember { mutableStateOf(today) }
    var reminderDate by remember { mutableStateOf(today) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Vaccination") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DateDropdownPicker(
                    label = "New Due Date",
                    currentDate = newDueDate,
                    onDateSelected = { 
                        newDueDate = it
                        // If reminder date is now after due date, pull it back
                        val due = sdf.parse(it)
                        val rem = sdf.parse(reminderDate)
                        if (due != null && rem != null && rem.after(due)) {
                            reminderDate = it
                        }
                    }
                )
                
                DateDropdownPicker(
                    label = "Reminder Date",
                    currentDate = reminderDate,
                    onDateSelected = { 
                        // Validation: Reminder date cannot be after due date
                        val due = sdf.parse(newDueDate)
                        val rem = sdf.parse(it)
                        if (due != null && rem != null && !rem.after(due)) {
                            reminderDate = it
                        } else {
                            // Show error or snap to due date
                            reminderDate = newDueDate
                        }
                    }
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Note/Reason (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(newDueDate, reminderDate, reason)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
