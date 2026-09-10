package com.neochildclinic.features.patient

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.core.utils.PatientUtils.cleanVaccineName
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay

/**
 * Vaccination History card (Patient Details -> Vaccination segment).
 * Layout mirrors ConsultationRecordCard's typography/spacing for visual
 * consistency between the two history segments.
 *
 * Row 1: vaccine name(s)                         | total fee (bold, prominent)
 * Row 2: "Next: <vaccine name(s) OR Type>"      | payment breakdown (Cash/Online, never "Mixed")
 * Row 3: "Given: <date>"                          | "Due: <date>"
 * Row 4: doctor name (secondary style)
 *
 * Next/Due information is sourced directly from the Reminder rows linked to
 * this visit (`reminders.original_visit_id == patient_visits.id`). The visit's
 * legacy next-vaccine fields are never used for this card.
 *
 * Tap -> onClick (open Vaccination Details). Long press -> onLongClick (bottom sheet).
 * No three-dot menu on the card itself.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaccinationRecordCard(
    vaccination: Vaccination,
    patient: Patient,
    doctorName: String = "",
    reminders: List<ReminderEntity> = emptyList(),
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

    // Group by Due Date: If dates are the same, show vaccine names separated by comma.
    // In "Next", only vaccine names are shown (falling back to Type if no vaccines).
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
            .sortedBy { com.neochildclinic.core.utils.PatientUtils.parseDate(it.second)?.time ?: Long.MAX_VALUE }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Row 1: Title (Vaccine name or Consultation problem) | total fee
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${vaccination.totalPaid.toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                }
            }

            // Row 2: Given date  |  payment breakdown
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(
                    text = "Given: ${formatDateForDisplay(vaccination.dateGiven)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Payment breakdown and small payment flags beneath it.
                val paymentInfo = buildString {
                    if (vaccination.cashAmount > 0) append("Cash: ₹${vaccination.cashAmount.toInt()}")
                    if (vaccination.cashAmount > 0 && vaccination.onlineAmount > 0) append(" | ")
                    if (vaccination.onlineAmount > 0) append("Online: ₹${vaccination.onlineAmount.toInt()}")
                }
                if (paymentInfo.isNotEmpty() || vaccination.withFees || vaccination.doctorsAcc) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (paymentInfo.isNotEmpty()) {
                            Text(
                                text = paymentInfo,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
                        }

                        val paymentFlags = buildList {
                            if (vaccination.withFees) add("With Fees")
                            if (vaccination.doctorsAcc) add("Doctor's Account")
                        }
                        if (paymentFlags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = paymentFlags.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            // Row 3+: Next vaccination(s) and their Due dates
            nextDisplays.forEach { (label, dueDate) ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        text = "Next: $label",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Due: ${formatDateForDisplay(dueDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
            }

            // Row 4: doctor name
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = displayDoctor,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
