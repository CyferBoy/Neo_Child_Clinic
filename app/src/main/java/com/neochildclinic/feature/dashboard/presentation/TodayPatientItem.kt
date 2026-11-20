package com.neochildclinic.feature.dashboard.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.designsystem.Spacing
import com.neochildclinic.domain.model.ConsultationTodo
import com.neochildclinic.domain.model.VaccinationTodo
import com.neochildclinic.domain.model.TimeRange

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodayPatientItem(
    index: Int,
    item: Any,
    slotRange: TimeRange? = null,
    isHighlighted: Boolean = false,
    onStatusToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onCreateConsultation: (() -> Unit)? = null,
    onAddPatient: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val status = when (item) {
        is ConsultationTodo -> item.status
        is VaccinationTodo -> item.status
        else -> "PENDING"
    }
    val isCompleted = status.equals("COMPLETED", ignoreCase = true) || status.equals("VISITED", ignoreCase = true)

    val nameLine = when (item) {
        is ConsultationTodo -> item.name
        is VaccinationTodo -> item.name
        else -> ""
    }
    val detailLine = when (item) {
        is ConsultationTodo -> item.address.ifBlank { "Consultation Visit" }
        is VaccinationTodo -> listOfNotNull(
            item.vaccineNames.takeIf { it.isNotBlank() },
            item.address.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        else -> ""
    }
    val doctorOrStaff = when (item) {
        is ConsultationTodo -> item.doctorName?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("Dr.", ignoreCase = true)) it else "Dr. $it" }
        is VaccinationTodo -> item.doctorName?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("Dr.", ignoreCase = true) || it.startsWith("Nurse", ignoreCase = true) || it.startsWith("Staff", ignoreCase = true)) it else "Staff $it" }
        else -> null
    }
    val phoneNumber = when (item) {
        is ConsultationTodo -> item.mobile
        is VaccinationTodo -> item.mobile
        else -> ""
    }

    // Soft palette for avatar container based on name hash
    val avatarContainers = listOf(
        Color(0xFFDCF4E4) to Color(0xFF054F31),
        Color(0xFFDCE9F8) to Color(0xFF0A2F52),
        Color(0xFFF8E4D7) to Color(0xFF5B2A12),
        Color(0xFFF0EAFB) to Color(0xFF3F2482),
        Color(0xFFFFF0C9) to Color(0xFF563400)
    )
    val colorPair = remember(nameLine) {
        val hash = kotlin.math.abs(nameLine.hashCode())
        avatarContainers[hash % avatarContainers.size]
    }

    val initials = remember(nameLine) {
        nameLine.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifEmpty { "P" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (isHighlighted) 2.dp else 1.dp,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.card, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: serial/queue number only (time slot removed per requirement)
            Column(
                modifier = Modifier.widthIn(min = 32.dp, max = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "#$index",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = Spacing.xs),
                color = MaterialTheme.colorScheme.outline
            )

            // Patient Initials Avatar Circle
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = colorPair.first
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorPair.second
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            // Main Info Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nameLine,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (detailLine.isNotBlank()) {
                    Text(
                        text = detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (doctorOrStaff != null) {
                    Text(
                        text = doctorOrStaff,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.width(Spacing.xs))

            // Status Pill Badge & Call Action
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCompleted) Color(0xFFDCF4E4) else Color(0xFFFFF0C9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isCompleted) Color(0xFF054F31) else Color(0xFF563400),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isCompleted) "Visited" else "Waiting",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompleted) Color(0xFF054F31) else Color(0xFF563400)
                        )
                    }
                }

                if (phoneNumber.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "Call",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { menuExpanded = false; onEdit() },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuExpanded = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
                DropdownMenuItem(
                    text = { Text(if (isCompleted) "Mark Waiting" else "Mark Visited") },
                    onClick = { menuExpanded = false; onStatusToggle() },
                    leadingIcon = { Icon(if (isCompleted) Icons.Default.Schedule else Icons.Default.Check, contentDescription = null) }
                )
                if (canCreateConsultationFromTodo(item, isCompleted, onCreateConsultation != null)) {
                    DropdownMenuItem(
                        text = { Text("Create Consultation") },
                        onClick = { menuExpanded = false; onCreateConsultation?.invoke() },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                    )
                }
                if (onAddPatient != null && shouldShowAddPatient(item)) {
                    DropdownMenuItem(
                        text = { Text("Add Patient") },
                        onClick = { menuExpanded = false; onAddPatient() },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }
                    )
                }
            }
        }
    }
}

/**
 * Create Consultation is offered only for a Waiting consultation todo that is linked to a
 * saved patient and is New (or a legacy blank visit type). Follow-up todos and completed
 * rows use the normal + flow.
 */
internal fun canCreateConsultationFromTodo(
    item: Any?,
    isCompleted: Boolean,
    hasCallback: Boolean
): Boolean {
    val todo = item as? ConsultationTodo ?: return false
    return hasCallback &&
        !isCompleted &&
        (todo.visitType.isBlank() || todo.visitType.equals("NEW", ignoreCase = true)) &&
        !todo.patientId.isNullOrBlank()
}

/** An unregistered row needs the Add Patient action so its todo can be linked to a saved patient. */
internal fun shouldShowAddPatient(item: Any?): Boolean {
    val patientId = when (item) {
        is ConsultationTodo -> item.patientId
        is VaccinationTodo -> item.patientId
        else -> return false
    }
    return patientId.isNullOrBlank()
}
