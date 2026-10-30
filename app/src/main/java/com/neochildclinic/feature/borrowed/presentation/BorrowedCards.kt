package com.neochildclinic.feature.borrowed.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.common.DateUtils
import java.time.LocalDate
import java.time.ZoneId

data class BorrowedStatusStyle(
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector
)

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
    val isDark = isSystemInDarkTheme()
    var menuExpanded by remember { mutableStateOf(false) }

    val isOverdue = remember(item.borrowedDate, item.remainingQuantity) {
        isLoanOverdue(item.borrowedDate, item.remainingQuantity)
    }

    val statusStyle = remember(item.status, isOverdue, isDark) {
        when {
            isOverdue -> BorrowedStatusStyle(
                label = "Return overdue",
                containerColor = if (isDark) Color(0xFF4E1511) else Color(0xFFFDE4E1),
                contentColor = if (isDark) Color(0xFFFFDAD6) else Color(0xFF7A1A12),
                icon = Icons.Default.Warning
            )
            item.status == BorrowStatus.PARTIALLY_RETURNED -> BorrowedStatusStyle(
                label = "Partially returned",
                containerColor = if (isDark) Color(0xFF4A3406) else Color(0xFFFFF0C9),
                contentColor = if (isDark) Color(0xFFFFE2A3) else Color(0xFF563400),
                icon = Icons.Default.Inventory2
            )
            item.status == BorrowStatus.BORROWED -> BorrowedStatusStyle(
                label = "Borrowed",
                containerColor = if (isDark) Color(0xFF17325C) else Color(0xFFE4EDFD),
                contentColor = if (isDark) Color(0xFFD6E4FF) else Color(0xFF0B357E),
                icon = Icons.Default.Event
            )
            else -> BorrowedStatusStyle(
                label = "Returned",
                containerColor = if (isDark) Color(0xFF054F31) else Color(0xFFDEF7EC),
                contentColor = if (isDark) Color(0xFFDEF7EC) else Color(0xFF03543F),
                icon = Icons.Default.CheckCircle
            )
        }
    }

    val initials = remember(item.vaccineName) {
        val words = item.vaccineName.trim().split(" ").filter { it.isNotBlank() }
        if (words.size >= 2) {
            "${words[0].first().uppercase()}${words[1].first().uppercase()}"
        } else {
            item.vaccineName.firstOrNull()?.uppercase() ?: "V"
        }
    }

    val prefix = if (item.type.equals("BY", ignoreCase = true)) "By" else "From"
    val doctorAttribution = remember(item.type, item.doctorName, item.notes) {
        buildString {
            append(prefix)
            append(" ")
            if (item.doctorName.isNotBlank()) {
                append(item.doctorName.trim())
            } else {
                append("External Clinic")
            }
            if (!item.notes.isNullOrBlank()) {
                append(" · ")
                append(item.notes.trim())
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) Color(0xFF151F1B) else Color.White,
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2C3A34) else Color(0xFFD8D2C6))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 40dp Circle Avatar matching SVG cx=56, cy=316, r=20
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF244A37) else Color(0xFFE0EBE2)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 13.6.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFC6E4D2) else Color(0xFF244032)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Line 1: Vaccine Name & Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.vaccineName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color(0xFFEEF3F0) else Color(0xFF14201F),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Status Badge (height 24dp, rx 12)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusStyle.containerColor,
                        modifier = Modifier.height(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = statusStyle.icon,
                                contentDescription = null,
                                tint = statusStyle.contentColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = statusStyle.label,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusStyle.contentColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Line 2: Batch Number
                Text(
                    text = "Batch ${item.batchNumber}",
                    fontSize = 9.5.sp,
                    color = if (isDark) Color(0xFF9AA8A1) else Color(0xFF5F6D68)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Line 3: Borrowed Date
                Text(
                    text = "Borrowed ${formatDisplayDate(item.borrowedDate)}",
                    fontSize = 9.5.sp,
                    color = if (isDark) Color(0xFF9AA8A1) else Color(0xFF5F6D68)
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Line 4: Quantities
                Text(
                    text = "${item.borrowedQuantity} borrowed · ${item.returnedQuantity} returned",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color(0xFFC9D3CE) else Color(0xFF384A46)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Line 5: Doctor attribution & Return Action Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = doctorAttribution,
                        fontSize = 9.5.sp,
                        color = if (isDark) Color(0xFF9AA8A1) else Color(0xFF5F6D68),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (item.remainingQuantity > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Return",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFF9CC7FF) else Color(0xFF16558F),
                            modifier = Modifier.clickable { onReturn() }
                        )
                    }
                }

                // Dropdown Context Menu for Actions
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

fun isLoanOverdue(borrowedDateStr: String, remainingQuantity: Int): Boolean {
    if (remainingQuantity <= 0 || borrowedDateStr.isBlank()) return false
    return try {
        val parsed = DateUtils.parseDate(borrowedDateStr) ?: return false
        val borrowedLocalDate = parsed.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        LocalDate.now().isAfter(borrowedLocalDate.plusDays(7))
    } catch (_: Exception) {
        false
    }
}

private fun formatDisplayDate(dateStr: String): String {
    if (dateStr.isBlank()) return "—"
    return DateUtils.formatDateForDisplay(dateStr)
}
