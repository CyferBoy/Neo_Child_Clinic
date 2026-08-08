package com.neochildclinic.features.patient

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
 * Row 2: "Next: <Type> • <next vaccine name(s)>"  | payment breakdown (Cash/Online, never "Mixed")
 * Row 3: "Given: <date>"                          | "Due: <date>"
 * Row 4: doctor name (secondary style)
 *
 * Next/Due information is sourced from the linked Due Vaccination record
 * (dueVaccination, the reminder_states row for this visit) rather than the
 * visit's own nxtVaccineNames/nextDueDate fields.
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
    dueVaccination: ReminderEntity? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onShowInventoryIssues: (String) -> Unit = {}
) {
    val displayDoctor = doctorName.ifBlank { vaccination.performedBy }.ifBlank { "Unknown Doctor" }
    val vaccineNamesText = vaccination.vaccineNames.joinToString(" • ") { cleanVaccineName(it) }

    // "Next:" text is built from the Due Vaccination record's Type (mandatory) and,
    // if present, its vaccine name(s) (optional) -- never shows an empty/"null" vaccine value.
    val nextVaccineNames = dueVaccination?.vaccineName?.trim().orEmpty()
    val nextLine = dueVaccination?.type?.trim()?.takeIf { it.isNotEmpty() }?.let { type ->
        if (nextVaccineNames.isNotEmpty()) "$type • $nextVaccineNames" else type
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Row 1: vaccine name(s)  |  total fee
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = vaccineNamesText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "₹${vaccination.totalPaid.toInt()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (vaccination.inventoryStatus == "FAILED" || vaccination.inventoryStatus == "PARTIAL" || vaccination.inventoryStatus == "PENDING") {
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onShowInventoryIssues(vaccination.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Inventory, contentDescription = "Inventory Error", tint = Color.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Row 2: Next vaccination (from Due Vaccination record)  |  payment breakdown
            if (nextLine != null || vaccination.cashAmount > 0 || vaccination.onlineAmount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (nextLine != null) {
                        Text(
                            text = "Next: $nextLine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Cash/Online breakdown -- never displays "Mixed"; shows only the
                    // method(s) that actually have an amount.
                    val paymentInfo = buildString {
                        if (vaccination.cashAmount > 0) append("Cash: ₹${vaccination.cashAmount.toInt()}")
                        if (vaccination.cashAmount > 0 && vaccination.onlineAmount > 0) append(" | ")
                        if (vaccination.onlineAmount > 0) append("Online: ₹${vaccination.onlineAmount.toInt()}")
                    }
                    if (paymentInfo.isNotEmpty()) {
                        Text(
                            text = paymentInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            // Row 3: Given date  |  Due date
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Given: ${formatDateForDisplay(vaccination.dateGiven)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!dueVaccination?.dueDate.isNullOrBlank()) {
                    Text(
                        text = "Due: ${formatDateForDisplay(dueVaccination!!.dueDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Row 4: doctor name
            Spacer(modifier = Modifier.height(4.dp))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
