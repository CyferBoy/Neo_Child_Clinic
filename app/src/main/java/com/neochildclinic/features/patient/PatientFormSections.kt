package com.neochildclinic.features.patient

import androidx.compose.foundation.layout.*
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

@Composable
fun DateSelectionSection(
    dob: String,
    onDobChange: (String) -> Unit,
    ageValue: String,
    onAgeValueChange: (String) -> Unit,
    ageUnit: String,
    onAgeUnitChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Date of Birth*",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
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
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Gender*",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Male", "Female", "Other").forEach { g ->
                FilterChip(
                    selected = selectedGender == g,
                    onClick = { onGenderChange(g) },
                    label = { Text(g) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
