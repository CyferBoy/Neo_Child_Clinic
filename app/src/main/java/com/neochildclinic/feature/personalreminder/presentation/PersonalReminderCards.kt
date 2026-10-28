package com.neochildclinic.feature.personalreminder.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.neochildclinic.core.common.DateCategory
import com.neochildclinic.core.common.DateClassifier
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.PersonalReminder
import com.neochildclinic.domain.model.PersonalReminderStatus
import com.neochildclinic.feature.statistics.domain.StatisticsUtils

@Composable
private fun reminderDateBadge(reminderDate: String?): Pair<String, Color> {
    if (reminderDate.isNullOrBlank()) return "No date set" to MaterialTheme.colorScheme.primary
    return when (val category = DateClassifier.classify(reminderDate)) {
        is DateCategory.Overdue -> "Overdue by ${category.days} day${if (category.days == 1) "" else "s"}" to MaterialTheme.colorScheme.error
        is DateCategory.Today -> "Today" to MaterialTheme.colorScheme.tertiary
        is DateCategory.Tomorrow -> "Tomorrow" to MaterialTheme.colorScheme.secondary
        is DateCategory.Future -> "Upcoming: ${category.dateStr}" to MaterialTheme.colorScheme.primary
    }
}

@Composable
fun PersonalReminderCard(
    reminder: PersonalReminder,
    patient: Patient?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = PersonalReminderStatus.fromRaw(reminder.status)
    val isActive = status == PersonalReminderStatus.PENDING || status == PersonalReminderStatus.READY
    val dateStr = reminder.reminderDate ?: ""
    val (dateText, dateColor) = if (isActive) reminderDateBadge(dateStr) else
        DateClassifier.formatDisplay(dateStr) to MaterialTheme.colorScheme.onSurfaceVariant

    val stripColor = when (status) {
        PersonalReminderStatus.PENDING -> MaterialTheme.colorScheme.tertiary
        PersonalReminderStatus.READY -> MaterialTheme.colorScheme.secondary
        PersonalReminderStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
        PersonalReminderStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .defaultMinSize(minHeight = 88.dp)
                    .fillMaxHeight()
                    .background(
                        stripColor,
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
            )

            Column(modifier = Modifier.weight(1f).padding(Spacing.card)) {
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

                Spacer(modifier = Modifier.height(Spacing.xs))

                Text(
                    text = "Patient: ${patient?.name?.takeIf { it.isNotBlank() } ?: reminder.patientName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (reminder.patientPhone.isNotBlank()) {
                    Text(
                        text = "Phone: ${reminder.patientPhone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (reminder.advanceReceived && reminder.advanceAmount != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Advance: " + StatisticsUtils.formatRupees(reminder.advanceAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Surface(
                    color = dateColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, dateColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
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
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = if (isActive) dateText else "${status.displayName}: $dateText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = dateColor
                        )
                    }
                }

                if (!reminder.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "Note: ${reminder.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: PersonalReminderStatus) {
    val (bgColor, fgColor) = when (status) {
        PersonalReminderStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        PersonalReminderStatus.READY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        PersonalReminderStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PersonalReminderStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fgColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
