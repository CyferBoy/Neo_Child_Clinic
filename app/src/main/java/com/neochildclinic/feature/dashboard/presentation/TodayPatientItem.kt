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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    // Long-press menu: Edit / Delete (existing behaviour)
    var menuExpanded by remember { mutableStateOf(false) }
    // Status-change confirmation dialog (requirement: long press initiates status change)
    var showStatusDialog by remember { mutableStateOf(false) }

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
                // Single tap: no status change — preserves existing navigation behaviour
                onClick = {},
                // Long press: open status-change dialog (per requirement)
                onLongClick = { showStatusDialog = true }
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

            // Long-press context menu: Edit / Delete
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }

    // Status-change dialog shown on long press (Edit/Delete dropdown is separate)
    if (showStatusDialog) {
        val targetStatus = if (isCompleted) "Waiting" else "Visited"
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Change Status", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Mark $nameLine as $targetStatus?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showStatusDialog = false
                    onStatusToggle()
                }) { Text("Mark $targetStatus") }
            },
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }) { Text("Cancel") }
            },
            // Also show Edit/Delete options for power users
            icon = {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    IconButton(onClick = { showStatusDialog = false; onEdit() }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showStatusDialog = false; onDelete() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
}
