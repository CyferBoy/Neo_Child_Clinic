package com.neochildclinic.features.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.core.designsystem.LocalCustomColors
import com.neochildclinic.domain.model.ConsultationTodo
import com.neochildclinic.domain.model.VaccinationTodo

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodayPatientItem(
    index: Int,
    item: Any,
    isHighlighted: Boolean = false,
    onStatusToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val status = when (item) {
        is ConsultationTodo -> item.status
        is VaccinationTodo -> item.status
        else -> "PENDING"
    }
    val isCompleted = status == "COMPLETED"

    val nameLine = when (item) {
        is ConsultationTodo -> item.name
        is VaccinationTodo -> "${item.name} (${item.vaccineNames})"
        else -> ""
    }
    val addressLine = when (item) {
        is ConsultationTodo -> item.address
        is VaccinationTodo -> item.address
        else -> ""
    }
    val phoneNumber = when (item) {
        is ConsultationTodo -> item.mobile
        is VaccinationTodo -> item.mobile
        else -> ""
    }

    val customColors = LocalCustomColors.current
    val color = if (item is ConsultationTodo) customColors.softBlue else customColors.softGreen
    val textColor = if (item is ConsultationTodo) customColors.textBlue else customColors.textGreen

    // Brief visual anchor for a notification-driven arrival (req. 9) - fades back to the
    // card's normal border once TodayPatientsScreen clears pendingHighlightId below.
    val highlightBorderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isHighlighted) textColor else Color.Transparent,
        label = "todayPatientHighlight"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isHighlighted) 2.dp else 0.dp,
                color = highlightBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = {},
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) color.copy(alpha = 0.5f) else color
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = if (isCompleted) 0.5f else 1f),
                modifier = Modifier.width(24.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = nameLine,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = if (isCompleted) 0.5f else 1f)
                )
                if (addressLine.isNotBlank()) {
                    Text(
                        text = addressLine,
                        fontSize = 13.sp,
                        color = textColor.copy(alpha = if (isCompleted) 0.3f else 0.7f)
                    )
                }
            }

            if (phoneNumber.isNotBlank()) {
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = textColor)
                }
            }

            IconButton(onClick = onStatusToggle) {
                Icon(
                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isCompleted) "Mark Pending" else "Mark Completed",
                    tint = if (isCompleted) Color(0xFF4CAF50) else textColor
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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