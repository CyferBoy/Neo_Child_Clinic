package com.neochildclinic.features.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay

private fun statusColor(status: BorrowStatus): Color = when (status) {
    BorrowStatus.BORROWED -> Color(0xFFE65100)
    BorrowStatus.PARTIALLY_RETURNED -> Color(0xFF1565C0)
    BorrowStatus.RETURNED -> Color(0xFF2E7D32)
}

private fun statusLabel(status: BorrowStatus): String = when (status) {
    BorrowStatus.BORROWED -> "Borrowed"
    BorrowStatus.PARTIALLY_RETURNED -> "Partially Returned"
    BorrowStatus.RETURNED -> "Returned"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowHistorySheet(
    item: BorrowedDisplayItem,
    onDismiss: () -> Unit,
    onMarkAsReturned: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(item.vaccineName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = statusColor(item.status).copy(alpha = 0.15f), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                    Text(
                        statusLabel(item.status),
                        color = statusColor(item.status),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "${item.borrowedQuantity} doses \u2022 Borrowed ${formatDateForDisplay(item.borrowedDate)} \u2022 ${item.batchNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Return History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))

            if (item.returns.isEmpty()) {
                Text(
                    "No returns recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                item.returns.forEachIndexed { index, ret ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Return ${item.returns.size - index}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Qty: ${ret.quantity}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Batch: ${ret.batchNumber}", style = MaterialTheme.typography.bodySmall)
                            if (ret.expiryDate.isNotBlank()) {
                                Text("Expiry: ${formatDateForDisplay(ret.expiryDate)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("Returned: ${formatDateForDisplay(ret.returnedDate)}", style = MaterialTheme.typography.bodySmall)
                            if (!ret.notes.isNullOrBlank()) {
                                Text(ret.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryStat("Borrowed", item.borrowedQuantity)
                SummaryStat("Returned", item.returnedQuantity)
                SummaryStat("Remaining", item.remainingQuantity)
            }

            if (item.remainingQuantity > 0) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onMarkAsReturned,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mark as Returned")
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
