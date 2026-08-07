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
    doctorName: String = "",
    onLongClick: () -> Unit = {},
    onShowInventoryIssues: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val displayDoctor = doctorName.ifBlank { vaccination.performedBy }.ifBlank { "Unknown Doctor" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { ReceiptManager.printReceipt(context, patient, vaccination, displayDoctor) },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = formatDateForDisplay(vaccination.dateGiven), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = vaccination.vaccineNames.joinToString(", ") { cleanVaccineName(it) }, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(text = "₹${vaccination.totalPaid}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    if (vaccination.nxtVaccineNames.isNotEmpty()) {
                        Text(text = "Next Vaccination:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = vaccination.nxtVaccineNames.joinToString(", ") { cleanVaccineName(it) }, 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (vaccination.nextDueDate.isNotBlank()) {
                        Text(text = "Due Date:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = formatDateForDisplay(vaccination.nextDueDate), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(modifier = Modifier.alpha(0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    val paymentInfo = buildString {
                        if (vaccination.cashAmount > 0) append("Cash: ₹${vaccination.cashAmount.toInt()}")
                        if (vaccination.cashAmount > 0 && vaccination.onlineAmount > 0) append(" | ")
                        if (vaccination.onlineAmount > 0) append("Online: ₹${vaccination.onlineAmount.toInt()}")
                    }
                    Text(text = paymentInfo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                        Spacer(Modifier.width(4.dp))
                        Text(text = displayDoctor, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                
                if (vaccination.inventoryStatus == "FAILED" || vaccination.inventoryStatus == "PARTIAL" || vaccination.inventoryStatus == "PENDING") {
                    IconButton(onClick = { onShowInventoryIssues(vaccination.id) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Inventory, contentDescription = "Inventory Error", tint = Color.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
