package com.neochildclinic.feature.reminder.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.DateDropdownPicker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun RescheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (newDate: String, reason: String) -> Unit
) {
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH) }
    val today = remember { LocalDate.now().format(fmt) }

    var newDueDate by remember { mutableStateOf(today) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Vaccination") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                DateDropdownPicker(
                    label = "New Due Date",
                    currentDate = newDueDate,
                    onDateSelected = { newDueDate = it }
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
                    onConfirm(newDueDate, reason)
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
