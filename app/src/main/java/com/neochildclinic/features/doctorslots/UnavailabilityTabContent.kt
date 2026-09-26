package com.neochildclinic.features.doctorslots

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.SlotExceptionType

@Composable
internal fun UnavailabilityTab(
    uiState: WeeklyDoctorSlotsUiState,
    onAddException: () -> Unit,
    onDeleteException: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Unavailability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (uiState.canManageSelectedDoctor) {
                TextButton(onClick = onAddException) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Exception")
                }
            }
        }

        if (uiState.exceptions.isEmpty()) {
            Text(
                "No exceptions configured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            uiState.exceptions.sortedByDescending { it.exceptionDate }.forEach { exception ->
                ExceptionRow(
                    exception = exception,
                    weeklySlotLabel = uiState.weeklySlots.firstOrNull { it.id == exception.weeklySlotId }?.timeRange?.label(),
                    canDelete = uiState.canManageSelectedDoctor,
                    onDelete = { onDeleteException(exception.id) }
                )
            }
        }
    }
}

@Composable
internal fun ExceptionRow(
    exception: DoctorSlotException,
    weeklySlotLabel: String?,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(exception.exceptionDate, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        exception.exceptionType == SlotExceptionType.FULL_DAY -> "Entire day unavailable"
                        exception.timeRangeLabel != null -> "Unavailable ${exception.timeRangeLabel}"
                        weeklySlotLabel != null -> "$weeklySlotLabel unavailable"
                        else -> "Slot unavailable"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (!exception.reason.isNullOrBlank()) {
                    Text("Reason: ${exception.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete exception", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}