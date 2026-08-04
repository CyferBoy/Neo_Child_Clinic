package com.neochildclinic.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.*

@Composable
fun FinanceHeader(filterMode: String, mainOptions: List<String>, onFilterModeChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Financial Analytics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        var expanded by remember { mutableStateOf(false) }
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(filterMode) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                mainOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onFilterModeChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RevenueOverviewCard(revenue: Double, filterMode: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(if (filterMode == "Overall") "Overall Revenue" else "Revenue ($filterMode)", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            Text("₹${String.format(Locale.getDefault(), "%,.0f", revenue)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun CashOnlineSummary(cash: Double, online: Double) {
    Row(modifier = Modifier.fillMaxWidth()) {
        FinanceStatItem(Modifier.weight(1f), "Cash Total", "₹${cash.toInt()}", Color(0xFF4CAF50))
        Spacer(modifier = Modifier.width(12.dp))
        FinanceStatItem(Modifier.weight(1f), "Online Total", "₹${online.toInt()}", Color(0xFF2196F3))
    }
}
