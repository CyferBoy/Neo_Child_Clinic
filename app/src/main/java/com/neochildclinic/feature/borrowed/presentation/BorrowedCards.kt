package com.neochildclinic.feature.borrowed.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.common.DateUtils
import com.neochildclinic.core.designsystem.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BorrowedRecordCard(
    item: BorrowedDisplayItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onReturn: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val (statusLabel, statusBg, statusFg) = when (item.status) {
        BorrowStatus.BORROWED -> Triple(
            "Borrowed",
            StatusWarningContainerLight,
            StatusWarningLight
        )
        BorrowStatus.PARTIALLY_RETURNED -> Triple(
            "Partially returned",
            StatusInfoContainerLight,
            StatusInfoLight
        )
        BorrowStatus.RETURNED -> Triple(
            "Returned",
            StatusSuccessContainerLight,
            StatusSuccessLight
        )
    }

    val initials = remember(item.vaccineName) {
        val words = item.vaccineName.trim().split(" ").filter { it.isNotBlank() }
        if (words.size >= 2) {
            "${words[0].first().uppercase()}${words[1].first().uppercase()}"
        } else {
            item.vaccineName.take(3).uppercase()
        }
    }

    val avatarColor = remember(item.vaccineName) {
        when {
            item.vaccineName.contains("Rota", ignoreCase = true) -> ClinicBlue
            item.vaccineName.contains("MMR", ignoreCase = true) -> ClinicSage
            item.vaccineName.contains("Hep", ignoreCase = true) -> ClinicAccent
            item.vaccineName.contains("BCG", ignoreCase = true) -> Color(0xFF6941C6)
            else -> ClinicBlue
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Row 1: Left 44dp Avatar + Vaccine Info + Return Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored Initials Avatar (44dp rounded box, rx 12)
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = avatarColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, avatarColor.copy(alpha = 0.25f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = avatarColor
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.md))

                // Vaccine Name & Batch / Date Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.vaccineName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Batch ${item.batchNumber} · Borrowed ${formatDisplayDate(item.borrowedDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Return Button (compact 36dp height, 10dp shape)
                if (item.remainingQuantity > 0) {
                    Button(
                        onClick = onReturn,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = "Return",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Row 2: Status chips & counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quantity breakdown chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "${item.borrowedQuantity} borrowed · ${item.returnedQuantity} returned",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }

                // Status Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }
            }

            // Row 3: Doctor / Clinic attribution at bottom
            val fromText = if (item.doctorName.isNotBlank()) "From ${item.doctorName}" else "From External Clinic"
            Text(
                text = fromText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Context Menu
            Box(modifier = Modifier.fillMaxWidth()) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (item.remainingQuantity > 0) {
                        DropdownMenuItem(
                            text = { Text("Return doses") },
                            leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                            onClick = {
                                menuExpanded = false
                                onReturn()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit record") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete record", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

private fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "—"
    return DateUtils.formatDateForDisplay(dateStr)
}
