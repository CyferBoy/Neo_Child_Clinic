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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.core.utils.PatientUtils.cleanVaccineName
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay
import com.neochildclinic.core.utils.ReceiptManager
import com.neochildclinic.core.ui.ActionDropdownMenu
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaccinationRecordCard(
    vaccination: Vaccination,
    patient: Patient,
    canEditOrDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowInventoryIssues: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { ReceiptManager.printReceipt(context, patient, vaccination) },
                onLongClick = { menuExpanded = true }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            VaccinationCardHeader(vaccination, onShowInventoryIssues)
            
            if (vaccination.nxtVaccineNames.isNotEmpty()) {
                val nextDisplayName = vaccination.nxtVaccineNames.joinToString(", ") { cleanVaccineName(it) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Next: $nextDisplayName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            if (vaccination.batchNumbers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Batch: ${vaccination.batchNumbers.joinToString(", ")} | Exp: ${vaccination.expiryDates.joinToString(", ")}", style = MaterialTheme.typography.labelSmall)
            }

            if (vaccination.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Notes: ${vaccination.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))
            VaccinationCardDates(vaccination)
        }

        Box(modifier = Modifier.align(Alignment.End)) {
            ActionDropdownMenu(
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onEdit = onEdit,
                onDelete = onDelete,
                onMarkAsDone = null,
                isAdmin = canEditOrDelete,
                onDownload = { 
                    (context as? androidx.activity.ComponentActivity)?.lifecycleScope?.launch {
                        ReceiptManager.downloadReceipt(context, patient, vaccination)
                    }
                }
            )
        }
    }
}

@Composable
fun VaccinationCardHeader(vaccination: Vaccination, onShowInventoryIssues: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        val displayName = vaccination.vaccineNames.joinToString(", ") { cleanVaccineName(it) }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            if (vaccination.inventoryStatus == "FAILED" || vaccination.inventoryStatus == "PARTIAL" || vaccination.inventoryStatus == "PENDING") {
                Surface(
                    color = Color(0xFFFFF3E0), // Orange tint
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { onShowInventoryIssues(vaccination.id) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Stock not updated", 
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))

        if (vaccination.totalPaid > 0) {
            PaymentInfoColumn(vaccination)
        } else if (vaccination.cost <= 0.0) {
             Text(text = "FREE", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PaymentInfoColumn(vaccination: Vaccination) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text = "₹${vaccination.totalPaid}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        val method = when {
            vaccination.cashAmount > 0 && vaccination.onlineAmount > 0 -> "C: ₹${vaccination.cashAmount.toInt()} | O: ₹${vaccination.onlineAmount.toInt()}"
            vaccination.cashAmount > 0 -> "Cash"
            vaccination.onlineAmount > 0 -> "Online"
            else -> ""
        }
        if (method.isNotBlank()) {
            Text(text = method, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (vaccination.withFees) {
            Text(text = "+ Fees", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        if (vaccination.doctorsAcc) {
            Text(text = "Dr. Acc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun VaccinationCardDates(vaccination: Vaccination) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = "DATE GIVEN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val dateGivenDisplay = remember(vaccination.dateGiven) { formatDateForDisplay(vaccination.dateGiven) }
            Text(text = dateGivenDisplay, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "NEXT DUE DATE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nextDueDateDisplay = if (vaccination.nextDueDate.isBlank()) "None" else formatDateForDisplay(vaccination.nextDueDate)
                Text(
                    text = nextDueDateDisplay, 
                    style = MaterialTheme.typography.bodySmall, 
                    fontWeight = FontWeight.Medium, 
                    color = if (vaccination.nextDueDate.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
