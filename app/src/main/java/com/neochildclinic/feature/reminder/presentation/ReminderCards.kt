package com.neochildclinic.feature.reminder.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.common.DateClassifier
import com.neochildclinic.core.common.DateCategory

import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.style.TextOverflow
import com.neochildclinic.core.common.PatientUtils
import com.neochildclinic.domain.model.*

@Composable
fun CompletedDismissedSummaryCards(
    completedCount: Int,
    dismissedCount: Int,
    onCompletedClick: () -> Unit,
    onDismissedClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Card(
            onClick = onCompletedClick,
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = LocalCustomColors.current.textGreen
                )
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Card(
            onClick = onDismissedClick,
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Dismissed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DuePatientCard(
    vaccination: Vaccination, 
    patient: Patient?,
    onLongPress: () -> Unit,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            // First Line: Patient Name (left) | Call icon (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = patient?.name ?: "Unknown Patient",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (patient != null && patient.phone.isNotBlank()) {
                    // Was sizing the IconButton itself, which overrode
                    // minimumInteractiveComponentSize and left a 32dp target.
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.phone}"))
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Call ${patient.name}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Second Line: Due Date
            Text(
                text = if (vaccination.nextDueDate.isBlank()) "No Date" else "Due: ${DateClassifier.formatDisplay(vaccination.nextDueDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Third Line: Next Vaccine or Type. If any vaccine names exist, show only vaccine names.
            val nextDisplay = vaccination.nextVaccinations
                .flatMap { if (it.vaccineNames.isNotEmpty()) it.vaccineNames else listOf(it.type) }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
            Text(
                text = nextDisplay.ifBlank { "Next Vaccination" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.sm))

            // Fourth Line: Status Badge
            // The badge text used raw #4CAF50 (2.71:1 on white) and #FBC02D
            // (~1.9:1) - both below WCAG AA for text, on the most status-critical
            // element in the app. Now uses the verified textGreen/textOrange pair
            // over its own soft container, which is also what the other pastel
            // status surfaces in this app already do.
            val customColors = LocalCustomColors.current
            val category = DateClassifier.classify(vaccination.nextDueDate)
            val statusText: String
            val statusBg: Color
            val statusFg: Color
            when (category) {
                is DateCategory.Overdue -> {
                    statusText = "${category.days} Days Overdue"
                    statusBg = MaterialTheme.colorScheme.errorContainer
                    statusFg = MaterialTheme.colorScheme.error
                }
                is DateCategory.Today -> {
                    statusText = "Due Today"
                    statusBg = customColors.softOrange
                    statusFg = customColors.textOrange
                }
                is DateCategory.Tomorrow -> {
                    statusText = "Due Tomorrow"
                    statusBg = customColors.softGreen
                    statusFg = customColors.textGreen
                }
                is DateCategory.Future -> {
                    val targetDate = PatientUtils.parseDate(vaccination.nextDueDate)
                    val diff = if (targetDate != null) {
                        val diffMs = targetDate.time - DateClassifier.getTodayStart().timeInMillis
                        java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMs).toInt()
                    } else 0
                    statusText = "Due in $diff Days"
                    statusBg = customColors.softGreen
                    statusFg = customColors.textGreen
                }
            }

            Surface(
                color = statusBg,
                shape = MaterialTheme.shapes.extraSmall,
                border = androidx.compose.foundation.BorderStroke(1.dp, statusFg)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (category) {
                            is DateCategory.Overdue -> Icons.Default.Error
                            is DateCategory.Today -> Icons.Default.Today
                            is DateCategory.Tomorrow,
                            is DateCategory.Future -> Icons.Default.Schedule
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = statusFg
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusFg
                    )
                }
            }
        }
    }
}

@Composable
fun CompletedRecordCard(
    vaccination: Vaccination,
    patient: Patient?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = patient?.name ?: "Unknown Patient",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (patient != null && patient.phone.isNotBlank()) {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.phone}"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Call ${patient.name}", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                text = vaccination.nxtVaccineNames.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("COMPLETED ON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vaccination.dateGiven, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ORIGINAL DUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vaccination.nextDueDate, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DismissedRecordCard(
    vaccination: Vaccination,
    patient: Patient?,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = patient?.name ?: "Unknown Patient",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (patient != null && patient.phone.isNotBlank()) {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.phone}"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Call ${patient.name}", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                text = vaccination.nxtVaccineNames.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("DISMISSED ON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vaccination.dateGiven, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ORIGINAL DUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vaccination.nextDueDate, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (vaccination.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text("Reason: ${vaccination.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

