package com.neochildclinic.feature.doctor.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.designsystem.Spacing
import com.neochildclinic.domain.model.DoctorSlotException
import com.neochildclinic.domain.model.SlotExceptionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun UnavailabilityTab(
    uiState: WeeklyDoctorSlotsUiState,
    onAddException: () -> Unit,
    onDeleteException: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Unavailability",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (uiState.canManageSelectedDoctor) {
                TextButton(onClick = onAddException) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = "Add exception",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (uiState.exceptions.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier.padding(Spacing.xl).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No unavailability exceptions configured.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            uiState.exceptions.sortedByDescending { it.exceptionDate }.forEach { exception ->
                ExceptionCard(
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
internal fun ExceptionCard(
    exception: DoctorSlotException,
    weeklySlotLabel: String?,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    val formattedDatePill = try {
        val date = LocalDate.parse(exception.exceptionDate)
        date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH))
    } catch (_: Exception) {
        exception.exceptionDate
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.card)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Soft terracotta date pill badge matching prototype
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8E4D7),
                    modifier = Modifier.size(width = 54.dp, height = 36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = formattedDatePill,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5B2A12),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                Column {
                    Text(
                        text = when {
                            exception.exceptionType == SlotExceptionType.FULL_DAY -> "Entire day unavailable"
                            exception.timeRangeLabel != null -> "Unavailable: ${exception.timeRangeLabel}"
                            weeklySlotLabel != null -> "$weeklySlotLabel unavailable"
                            else -> "Slot unavailable"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!exception.reason.isNullOrBlank()) {
                        Text(
                            text = exception.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete exception",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
