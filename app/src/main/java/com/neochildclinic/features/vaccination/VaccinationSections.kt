package com.neochildclinic.features.vaccination

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.Vaccine
import com.neochildclinic.core.ui.StandardAutoCompleteField
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.ui.StandardButton

@Composable
fun VaccineSelectionSection(
    inventory: List<Vaccine>,
    selectedVaccines: List<String>,
    onVaccineSelected: (Vaccine) -> Unit,
    onRemoveVaccine: (Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val filteredInventory = remember(query, inventory) {
        inventory.filter { 
            it.brandName.contains(query, true) || it.type.contains(query, true) 
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StandardAutoCompleteField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = "Select Vaccine*",
            placeholder = "Search inventory...",
            expanded = expanded,
            onExpandedChange = { expanded = it },
            dropdownContent = {
                if (filteredInventory.isNotEmpty()) {
                    filteredInventory.forEach { v ->
                        val isOutOfStock = v.stock <= 0
                        val isLowStock = v.isLowStock

                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isOutOfStock) "${v.brandName} (Out of Stock)" else v.brandName,
                                            color = if (isOutOfStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isLowStock) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            ) {
                                                Text(
                                                    text = "Low Stock",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                    Text(v.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingIcon = { 
                                Text(
                                    text = "Stock: ${v.stock}", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOutOfStock || isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                ) 
                            },
                            enabled = true,
                            onClick = { onVaccineSelected(v); query = ""; expanded = false }
                        )
                    }
                } else if (query.isNotBlank()) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text("No matching vaccines in inventory", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        )

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selectedVaccines.forEachIndexed { index, name ->
                InputChip(
                    selected = true,
                    onClick = { },
                    label = { Text(name) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp).clickable { onRemoveVaccine(index) }) }
                )
            }
        }
    }
}

@Composable
fun PaymentSection(
    cash: String,
    online: String,
    total: Double,
    cost: String,
    withFees: Boolean,
    doctorsAcc: Boolean,
    onCashChange: (String) -> Unit,
    onOnlineChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onFeesToggle: (Boolean) -> Unit,
    onAccToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Payment", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StandardTextField(
                value = cash,
                onValueChange = onCashChange,
                label = "Cash",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            StandardTextField(
                value = online,
                onValueChange = onOnlineChange,
                label = "Online",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        val totalPaidDisplay = if (total % 1.0 == 0.0) total.toInt().toString() else total.toString()
        StandardTextField(
            value = totalPaidDisplay,
            onValueChange = {},
            label = "Total",
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = withFees,
                    onCheckedChange = onFeesToggle
                )
                Text("With Fees")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = doctorsAcc,
                    onCheckedChange = onAccToggle
                )
                Text("Doctor's Account")
            }
        }
    }
}

@Composable
fun ActionButtons(isLoading: Boolean, isEdit: Boolean, onSave: () -> Unit, onSaveAndDownload: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StandardButton(onClick = onSave, isLoading = isLoading, modifier = Modifier.weight(1f)) {
            Text(if (isEdit) "Update" else "Save")
        }
        if (!isEdit) {
            StandardButton(onClick = onSaveAndDownload, isLoading = isLoading, containerColor = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1.5f)) {
                Icon(Icons.Default.Print, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save & Download")
            }
        }
    }
}
