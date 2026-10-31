package com.neochildclinic.feature.vaccination.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(Spacing.card), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Vaccine & batch",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isOnlyRow && allowVaccineBatchEdit) {
                    TextButton(
                        onClick = onRemove,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "Remove",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFB42318)
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
                    placeholder = "Select Vaccine"
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Quantity",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${state.quantity} dose${if (state.quantity == 1) "" else "s"}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (allowQuantityEdit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF0ECE3),
                            onClick = {
                                if (state.quantity > 1) {
                                    onQuantityChange((state.quantity - 1).toString())
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease",
                                    tint = Color(0xFF384A46),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = state.quantity.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = Spacing.xs)
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF0ECE3),
                            onClick = {
                                val maxStock = state.selectedBatch?.remainingQuantity ?: 999
                                if (state.quantity < maxStock) {
                                    onQuantityChange((state.quantity + 1).toString())
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase",
                                    tint = Color(0xFF384A46),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
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