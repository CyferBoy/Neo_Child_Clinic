package com.neochildclinic.features.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.DateDropdownPicker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun RescheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH) }
    val today = remember { LocalDate.now().format(fmt) }

    fun parse(s: String): LocalDate? = try { LocalDate.parse(s, fmt) } catch (_: Exception) { null }

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
                        val due = parse(it)
                        val rem = parse(reminderDate)
                        if (due != null && rem != null && rem.isAfter(due)) {
                            reminderDate = it
                        }
                    }
                )
                
                DateDropdownPicker(
                    label = "Reminder Date",
                    currentDate = reminderDate,
                    onDateSelected = { 
                        // Validation: Reminder date cannot be after due date
                        val due = parse(newDueDate)
                        val rem = parse(it)
                        if (due != null && rem != null && !rem.isAfter(due)) {
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
