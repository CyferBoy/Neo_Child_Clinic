package com.neochildclinic.feature.vaccination.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.StandardAutoCompleteField
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.VaccineBatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaccineRow(
    state: VaccineSelectionState,
    inventory: List<InventoryItem>,
    givenDate: String,
    onVaccineSelected: (InventoryItem) -> Unit,
    onBatchSelected: (VaccineBatch) -> Unit,
    onQuantityChange: (String) -> Unit,
    allowVaccineBatchEdit: Boolean,
    allowQuantityEdit: Boolean,
    onRemove: () -> Unit,
    isOnlyRow: Boolean
) {
    var vaccineSearch by remember { mutableStateOf(state.selectedVaccine?.brandName ?: "") }
    var vaccineExpanded by remember { mutableStateOf(false) }
    var batchExpanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vaccine Row", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (!isOnlyRow && allowVaccineBatchEdit) {
                    // Was Modifier.size(24.dp) on the IconButton, which overrode
                    // minimumInteractiveComponentSize and left a 24dp touch target.
                    // Size the icon instead and let the button keep its 48dp target.
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove vaccine row",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (allowVaccineBatchEdit) {
                StandardAutoCompleteField(
                    value = vaccineSearch,
                    onValueChange = { vaccineSearch = it; vaccineExpanded = true },
                    label = "Vaccine*",
                    expanded = vaccineExpanded,
                    onExpandedChange = { vaccineExpanded = it },
                    placeholder = "Search Vaccine"
                ) {
                    val filtered = inventory.filter { it.brandName.contains(vaccineSearch, ignoreCase = true) }
                    filtered.forEach { vaccine ->
                        DropdownMenuItem(
                            text = { Text("${vaccine.brandName} (${vaccine.stock} in stock)") },
                            onClick = {
                                onVaccineSelected(vaccine)
                                vaccineSearch = vaccine.brandName
                                vaccineExpanded = false
                            }
                        )
                    }
                }
            } else {
                ReadOnlyValue("Vaccine: ${state.selectedVaccine?.brandName ?: "Not selected"}")
            }

            val batches = state.selectedVaccine?.batches?.filter {
                it.remainingQuantity > 0 && !com.neochildclinic.feature.inventory.domain.InventoryUtils.isExpiredAsOf(it.expiryDate, givenDate)
            } ?: emptyList()
            if (allowVaccineBatchEdit) {
                ExposedDropdownMenuBox(
                    expanded = batchExpanded,
                    onExpandedChange = { if (batches.isNotEmpty()) batchExpanded = it }
                ) {
                    StandardTextField(
                        value = state.selectedBatch?.let { "${it.batchNumber} (Qty: ${it.remainingQuantity})" } ?: "Select Batch",
                        onValueChange = {},
                        readOnly = true,
                        label = "Batch*",
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = batchExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = batchExpanded,
                        onDismissRequest = { batchExpanded = false }
                    ) {
                        batches.forEach { batch ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(batch.batchNumber, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "Qty: ${batch.remainingQuantity} | Exp: ${batch.expiryDate}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                onClick = { onBatchSelected(batch); batchExpanded = false }
                            )
                        }
                    }
                }
            } else {
                ReadOnlyValue("Batch: ${state.selectedBatch?.batchNumber ?: "No batch"}")
            }

            if (state.selectedBatch != null) {
                val expired =
                    com.neochildclinic.feature.inventory.domain.InventoryUtils.isExpiredAsOf(state.selectedBatch.expiryDate, givenDate)
                Text(
                    text = if (expired) "Expired: ${state.selectedBatch.expiryDate}"
                            else "Expiry: ${state.selectedBatch.expiryDate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (expired) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StandardTextField(
                value = state.quantity.toString(),
                onValueChange = onQuantityChange,
                label = "Quantity*",
                readOnly = !allowQuantityEdit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

@Composable
internal fun ReadOnlyValue(value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        // Was 10dp - a radius in no scale. Matches the text fields it stands in for.
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            value,
            modifier = Modifier.padding(Spacing.md),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}