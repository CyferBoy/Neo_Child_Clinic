package com.neochildclinic.features.personalreminder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.utils.DateCategory
import com.neochildclinic.core.utils.DateClassifier
import com.neochildclinic.data.local.entity.PersonalReminderEntity
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.PersonalReminderStatus
import java.util.Locale

private fun statusColor(status: PersonalReminderStatus): Color = when (status) {
    PersonalReminderStatus.PENDING -> Color(0xFFFB8C00)
    PersonalReminderStatus.READY -> Color(0xFF2E7D32)
    PersonalReminderStatus.COMPLETED -> Color(0xFF2E7D32)
    PersonalReminderStatus.CANCELLED -> Color(0xFF9E9E9E)
}

/**
 * Reminder-date badge text/color, reusing the app-wide [DateClassifier] so "Overdue" /
 * "Today" / "Upcoming" behave identically to the Next Vaccination due list.
 */
@Composable
private fun reminderDateBadge(reminderDate: String?): Pair<String, Color> {
    if (reminderDate.isNullOrBlank()) return "No date set" to MaterialTheme.colorScheme.primary
    return when (val category = DateClassifier.classify(reminderDate)) {
        is DateCategory.Overdue -> "Overdue by ${category.days} day${if (category.days == 1) "" else "s"}" to MaterialTheme.colorScheme.error
        is DateCategory.Today -> "Today" to Color(0xFFFBC02D)
        is DateCategory.Tomorrow -> "Tomorrow" to Color(0xFF4CAF50)
        is DateCategory.Future -> "Upcoming: ${category.dateStr}" to Color(0xFF4CAF50)
    }
}

@Composable
fun PersonalReminderCard(
    reminder: PersonalReminderEntity,
    patient: Patient?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = PersonalReminderStatus.fromRaw(reminder.status)
    val isActive = status == PersonalReminderStatus.PENDING || status == PersonalReminderStatus.READY
    val dateStr = reminder.reminderDate ?: ""
    val (dateText, dateColor) = if (isActive) reminderDateBadge(dateStr) else
        DateClassifier.formatDisplay(dateStr) to MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = reminder.vaccineLabel?.takeIf { it.isNotBlank() } ?: "Vaccine Requirement",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusChip(status)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Patient: ${patient?.name?.takeIf { it.isNotBlank() } ?: reminder.patientName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (reminder.patientPhone.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("Phone: ${reminder.patientPhone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (reminder.advanceReceived && reminder.advanceAmount != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Advance: " + String.format(Locale.US, "\u20b9%,.0f", reminder.advanceAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = dateColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, dateColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            !isActive -> Icons.Default.EventAvailable
                            dateStr.isNotBlank() && DateClassifier.classify(dateStr) is DateCategory.Overdue -> Icons.Default.Error
                            else -> Icons.Default.Schedule
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = dateColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isActive) dateText else "${status.displayName}: $dateText",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = dateColor
                    )
                }
            }

            if (!reminder.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = reminder.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatusChip(status: PersonalReminderStatus) {
    val color = statusColor(status)
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
