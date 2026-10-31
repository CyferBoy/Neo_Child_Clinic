package com.neochildclinic.feature.vaccination.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.SelectDropdown
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.designsystem.*
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Next vaccination",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (group.items.any { it.reminderId != null }) {
                    TextButton(
                        onClick = { showCancelGroupDialog = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "Cancel",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFB42318)
                        )
                    }
                } else {
                    TextButton(
                        onClick = onRemoveGroup,
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

            DateDropdownPicker(
                label = "Due date *",
                currentDate = group.dueDate,
                onDateSelected = onDueDateSelected,
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            group.items.forEachIndexed { index, item ->
                NextVaccinationItemRow(
                    item = item,
                    inventory = inventory,
                    availableTypes = availableTypes,
                    isOnlyItem = group.items.size == 1,
                    onTypeSelected = { onTypeSelected(item.id, it) },
                    onVaccineSelected = { onVaccineSelected(item.id, it) },
                    onRemove = { onRemoveItem(item.id) },
                    onCancel = { onCancelItem(item.id) }
                )
                if (index < group.items.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            TextButton(
                onClick = onAddItem,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "Add another vaccine",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
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
    isOnlyItem: Boolean,
    onTypeSelected: (String) -> Unit,
    onVaccineSelected: (InventoryItem?) -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit
) {
    var vaccineExpanded by remember { mutableStateOf(false) }
    var showCancelItemDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (!isOnlyItem) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vaccine & type",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.reminderId != null) {
                    TextButton(
                        onClick = { showCancelItemDialog = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "Cancel item",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFB42318)
                        )
                    }
                } else {
                    TextButton(
                        onClick = onRemove,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "Remove",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFFB42318)
                        )
                    }
                }
            }
        }

        SelectDropdown(
            items = availableTypes,
            selected = item.type,
            onItemSelected = onTypeSelected,
            itemLabel = { it },
            label = "Vaccine type *",
            isError = item.typeError,
            errorText = "Type selection is mandatory"
        )

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
                value = item.vaccine?.brandName ?: if (item.type.isBlank()) "Select a type first" else "None (Optional)",
                onValueChange = {},
                readOnly = true,
                label = "Specific vaccine (Optional)",
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
