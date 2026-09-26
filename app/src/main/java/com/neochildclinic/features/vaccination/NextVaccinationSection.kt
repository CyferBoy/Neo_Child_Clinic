package com.neochildclinic.features.vaccination

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.SelectDropdown
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.domain.model.InventoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NextVaccinationGroupCard(
    group: NextVaccinationGroup,
    inventory: List<InventoryItem>,
    availableTypes: List<String>,
    onDueDateSelected: (String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveGroup: () -> Unit,
    onCancelGroup: () -> Unit,
    onTypeSelected: (String, String) -> Unit,
    onVaccineSelected: (String, InventoryItem?) -> Unit,
    onRemoveItem: (String) -> Unit,
    onCancelItem: (String) -> Unit
) {
    var showCancelGroupDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.weight(1f)) {
                    DateDropdownPicker(
                        label = "Due Date*",
                        currentDate = group.dueDate,
                        onDateSelected = onDueDateSelected
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (group.items.any { it.reminderId != null }) {
                    TextButton(onClick = { showCancelGroupDialog = true }) {
                        Text("Cancel Group", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    IconButton(onClick = onRemoveGroup) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove date group", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            group.items.forEachIndexed { index, item ->
                NextVaccinationItemRow(
                    item = item,
                    inventory = inventory,
                    availableTypes = availableTypes,
                    onTypeSelected = { onTypeSelected(item.id, it) },
                    onVaccineSelected = { onVaccineSelected(item.id, it) },
                    onRemove = { onRemoveItem(item.id) },
                    onCancel = { onCancelItem(item.id) }
                )
                if (index < group.items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            TextButton(
                onClick = onAddItem,
                modifier = Modifier.align(Alignment.Start),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Vaccine/Type")
            }
        }
    }

    if (showCancelGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCancelGroupDialog = false },
            title = { Text("Cancel Group") },
            text = { Text("Cancel all vaccinations scheduled for ${group.dueDate}?") },
            confirmButton = {
                TextButton(onClick = { showCancelGroupDialog = false; onCancelGroup() }) {
                    Text("Cancel All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelGroupDialog = false }) { Text("Keep") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NextVaccinationItemRow(
    item: NextVaccinationItem,
    inventory: List<InventoryItem>,
    availableTypes: List<String>,
    onTypeSelected: (String) -> Unit,
    onVaccineSelected: (InventoryItem?) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit
) {
    var vaccineExpanded by remember { mutableStateOf(false) }
    var showCancelItemDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                SelectDropdown(
                    items = availableTypes,
                    selected = item.type,
                    onItemSelected = onTypeSelected,
                    itemLabel = { it },
                    label = "Type*",
                    isError = item.typeError,
                    errorText = "Type selection is mandatory"
                )
            }

            if (item.reminderId != null) {
                IconButton(onClick = { showCancelItemDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel item", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            } else {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove item", tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }
        }

        val filteredVaccines = if (item.type.isBlank()) {
            emptyList()
        } else {
            inventory.filter { it.type.equals(item.type, ignoreCase = true) }
        }

        ExposedDropdownMenuBox(
            expanded = vaccineExpanded,
            onExpandedChange = { if (item.type.isNotBlank()) vaccineExpanded = it }
        ) {
            StandardTextField(
                value = item.vaccine?.brandName ?: if (item.type.isBlank()) "Select a Type first" else "None (Optional)",
                onValueChange = {},
                readOnly = true,
                label = "Vaccine",
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = vaccineExpanded) }
            )
            ExposedDropdownMenu(
                expanded = vaccineExpanded,
                onDismissRequest = { vaccineExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("None") },
                    onClick = { onVaccineSelected(null); vaccineExpanded = false },
                    leadingIcon = { if (item.vaccine == null) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                )
                filteredVaccines.forEach { vaccine ->
                    val isSelected = item.vaccine?.id == vaccine.id
                    DropdownMenuItem(
                        text = { Text(vaccine.brandName) },
                        leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) } } else null,
                        onClick = { onVaccineSelected(vaccine); vaccineExpanded = false }
                    )
                }
            }
        }
    }

    if (showCancelItemDialog) {
        AlertDialog(
            onDismissRequest = { showCancelItemDialog = false },
            title = { Text("Cancel Item") },
            text = { Text("Cancel this ${item.type} ${item.vaccine?.brandName ?: ""} reminder?") },
            confirmButton = {
                TextButton(onClick = { showCancelItemDialog = false; onCancel() }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelItemDialog = false }) { Text("Keep") }
            }
        )
    }
}