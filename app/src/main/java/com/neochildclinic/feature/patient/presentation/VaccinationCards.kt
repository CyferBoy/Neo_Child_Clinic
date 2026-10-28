package com.neochildclinic.feature.patient.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.core.common.PatientUtils.cleanVaccineName
import com.neochildclinic.core.common.PatientUtils.formatDateForDisplay
import com.neochildclinic.core.designsystem.*

/**
 * Vaccination History card (Patient Details -> Vaccination segment).
 * Redesigned to Warm Clinical System v2 specifications:
 * - Flat 20dp radius card with 1dp outline border
 * - Clinical Sage / Harbor Blue accent pill
 * - Clear amount hierarchy and next due status pill
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaccinationRecordCard(
    vaccination: Vaccination,
    patient: Patient,
    doctorName: String = "",
    reminders: List<Reminder> = emptyList(),
    vaccineMap: Map<String, String> = emptyMap(),
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onShowInventoryIssues: (String) -> Unit = {}
) {
    val displayDoctor = doctorName.ifBlank { vaccination.performedBy }.ifBlank { "Unknown Doctor" }
    val vaccineNamesText = vaccination.vaccineNames.joinToString(" • ") { cleanVaccineName(it) }
    val displayTitle = if (vaccination.visitType == "CONSULTATION") {
        vaccination.notes.ifBlank { "Consultation" }
    } else {
        vaccineNamesText.ifBlank { vaccination.notes }.ifBlank { "Vaccination Visit" }
    }

    val nextDisplays = remember(reminders) {
        reminders
            .filter { it.dueDate.isNotBlank() }
            .groupBy { it.dueDate }
            .map { (dueDate, group) ->
                val labels = group.map { reminder ->
                    val names = reminder.vaccineName
                        .split(",")
                        .map(String::trim)
                        .filter(String::isNotBlank)
                    if (names.isNotEmpty()) {
                        names.distinct().joinToString(", ")
                    } else {
                        reminder.type.trim().ifBlank { "Next Vaccination" }
                    }
                }.distinct()

                val label = labels.joinToString(", ")
                label to dueDate
            }
            .sortedBy { com.neochildclinic.core.common.PatientUtils.parseDate(it.second)?.time ?: Long.MAX_VALUE }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left color accent strip
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .defaultMinSize(minHeight = 100.dp)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.secondary,
                        RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.card)
            ) {
                // Row 1: Vaccine name & Total Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Vaccines,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Given: ${formatDateForDisplay(vaccination.dateGiven)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "₹${vaccination.totalPaid.toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Row 2: Doctor and Payment Info
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = displayDoctor,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    val paymentInfo = buildString {
                        if (vaccination.cashAmount > 0) append("Cash: ₹${vaccination.cashAmount.toInt()}")
                        if (vaccination.cashAmount > 0 && vaccination.onlineAmount > 0) append(" · ")
                        if (vaccination.onlineAmount > 0) append("Online: ₹${vaccination.onlineAmount.toInt()}")
                    }
                    if (paymentInfo.isNotEmpty()) {
                        Text(
                            text = paymentInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Row 3: Next vaccination status pills or Series completed
                if (nextDisplays.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        nextDisplays.forEach { (label, dueDate) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Next: $label · ${formatDateForDisplay(dueDate)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Series completed",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
