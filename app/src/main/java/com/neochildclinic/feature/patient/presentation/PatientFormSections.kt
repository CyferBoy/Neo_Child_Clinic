package com.neochildclinic.feature.patient.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.ui.StandardAutoCompleteField
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.designsystem.*

@Composable
fun DateSelectionSection(
    dob: String,
    onDobChange: (String) -> Unit,
    ageValue: String,
    onAgeValueChange: (String) -> Unit,
    ageUnit: String,
    onAgeUnitChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Date of Birth *",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = Spacing.xs)
        )
        
        DateDropdownPicker(
            label = "",
            currentDate = dob,
            onDateSelected = onDobChange,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Or Set Age",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            StandardTextField(
                value = ageValue,
                onValueChange = onAgeValueChange,
                label = "Age",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            
            var expandedUnit by rememberSaveable { mutableStateOf(false) }
            StandardAutoCompleteField(
                value = ageUnit,
                onValueChange = { },
                label = "Unit",
                expanded = expandedUnit,
                onExpandedChange = { expandedUnit = it },
                modifier = Modifier.weight(1f),
                dropdownContent = {
                    listOf("Years", "Months", "Weeks").forEach { unit ->
                        DropdownMenuItem(
                            text = { Text(unit) },
                            onClick = {
                                onAgeUnitChange(unit)
                                expandedUnit = false
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun GenderSelectionSection(
    selectedGender: String,
    onGenderChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Gender *",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = Spacing.xs)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            listOf("Female", "Male", "Other").forEach { g ->
                val isSelected = selectedGender.equals(g, ignoreCase = true)
                Surface(
                    onClick = { onGenderChange(g) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = g,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
