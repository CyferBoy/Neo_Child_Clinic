package com.neochildclinic.features.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay
import com.neochildclinic.data.local.entity.VaccineBatchEntity
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.repository.NewBatchInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnVaccineDialog(
    item: BorrowedDisplayItem,
    inventory: List<InventoryItem>,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Int, batchId: String, notes: String?, newBatchInfo: NewBatchInfo?) -> Unit
) {
    val vaccine = inventory.find { it.id == item.vaccineId }
    val originalBatch = vaccine?.batches?.find { it.batchId == item.record.batchId }

    var sameBatch by rememberSaveable { mutableStateOf(true) }
    var addNewBatch by rememberSaveable { mutableStateOf(false) }
    
    var quantityText by rememberSaveable { mutableStateOf(if (item.remainingQuantity > 0) "1" else "") }
    var selectedBatchId by rememberSaveable { mutableStateOf(originalBatch?.batchId ?: "") }
    var batchDropdownExpanded by remember { mutableStateOf(false) }
    
    // New Batch Fields
    var newBatchNumber by rememberSaveable { mutableStateOf("") }
    var newExpiryDate by rememberSaveable { mutableStateOf("") }
    var newSellingPrice by rememberSaveable { mutableStateOf(vaccine?.mrp?.toString() ?: "0") }

    val quantity = quantityText.toIntOrNull()
    val quantityError = when {
        quantityText.isBlank() -> null
        quantity == null -> "Enter a valid number."
        quantity <= 0 -> "Return quantity must be greater than 0."
        quantity > item.remainingQuantity -> "Cannot exceed remaining quantity (${item.remainingQuantity})."
        else -> null
    }

    // Always use the borrow record's batch ID for "Same Batch & Expiry".
    // The borrowed batch may have zero current stock and therefore may not be
    // present in the inventory list supplied to this dialog. Relying on
    // originalBatch here could produce an empty ID and make the return fail.
    val effectiveBatchId = if (sameBatch) item.record.batchId else selectedBatchId
    val batchError = if (!sameBatch && !addNewBatch && effectiveBatchId.isBlank()) "Select a batch." else null
    val newBatchError = if (!sameBatch && addNewBatch && (newBatchNumber.isBlank() || newExpiryDate.isBlank())) "Enter batch number and expiry." else null

    val canConfirm = quantity != null && quantityError == null && 
        (sameBatch || addNewBatch || effectiveBatchId.isNotBlank()) &&
        (!addNewBatch || (newBatchNumber.isNotBlank() && newExpiryDate.isNotBlank()))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return Vaccine") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Borrowed: ${item.borrowedQuantity}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Remaining: ${item.remainingQuantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                StandardTextField(
                    value = quantityText,
                    onValueChange = { new -> if (new.isEmpty() || new.all { it.isDigit() }) quantityText = new },
                    label = "Return Quantity",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                quantityError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableToggle { 
                            sameBatch = !sameBatch
                            if (sameBatch) addNewBatch = false
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = sameBatch, onCheckedChange = { 
                        sameBatch = it
                        if (it) addNewBatch = false
                    })
                    Text("Same Batch & Expiry", style = MaterialTheme.typography.bodyMedium)
                }

                if (sameBatch) {
                    Column {
                        Text(
                            "Batch: ${originalBatch?.batchNumber ?: item.batchNumber}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Expiry: ${formatDateForDisplay(originalBatch?.expiryDate ?: item.expiryDate)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    // Choose existing or Add new
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableToggle { addNewBatch = !addNewBatch },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = addNewBatch, onCheckedChange = { addNewBatch = it })
                        Text("Add New Batch Details", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (addNewBatch) {
                        StandardTextField(
                            value = newBatchNumber,
                            onValueChange = { newBatchNumber = it },
                            label = "New Batch Number",
                            modifier = Modifier.fillMaxWidth()
                        )
                        StandardTextField(
                            value = newExpiryDate,
                            onValueChange = { newExpiryDate = it },
                            label = "Expiry Date (yyyy-MM-dd)",
                            modifier = Modifier.fillMaxWidth()
                        )
                        StandardTextField(
                            value = newSellingPrice,
                            onValueChange = { newSellingPrice = it },
                            label = "Selling Price (Optional)",
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = batchDropdownExpanded,
                            onExpandedChange = { batchDropdownExpanded = it }
                        ) {
                            val selectedBatch = vaccine?.batches?.find { it.batchId == selectedBatchId }
                            OutlinedTextField(
                                value = selectedBatch?.let { "${it.batchNumber} (exp. ${formatDateForDisplay(it.expiryDate)})" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Existing Batch") },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = batchDropdownExpanded) },
                                isError = batchError != null
                            )
                            ExposedDropdownMenu(
                                expanded = batchDropdownExpanded,
                                onDismissRequest = { batchDropdownExpanded = false }
                            ) {
                                vaccine?.batches.orEmpty().forEach { batch: VaccineBatchEntity ->
                                    DropdownMenuItem(
                                        text = { Text("${batch.batchNumber} - exp. ${formatDateForDisplay(batch.expiryDate)}") },
                                        onClick = {
                                            selectedBatchId = batch.batchId
                                            batchDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    (batchError ?: newBatchError)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            StandardButton(
                onClick = { 
                    val newBatch = if (addNewBatch) {
                        NewBatchInfo(
                            batchNumber = newBatchNumber,
                            expiryDate = newExpiryDate,
                            sellingPrice = newSellingPrice.toDoubleOrNull() ?: 0.0
                        )
                    } else null
                    onConfirm(quantity ?: 0, effectiveBatchId, null, newBatch) 
                },
                enabled = canConfirm,
                modifier = Modifier.width(120.dp)
            ) {
                Text("Return")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun Modifier.clickableToggle(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
