package com.neochildclinic.features.patient

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.designsystem.NeoChildTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatientContent(
    isEditMode: Boolean,
    onBack: () -> Unit,
    clinicId: String,
    onClinicIdChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    alternatePhone: String,
    onAlternatePhoneChange: (String) -> Unit,
    dob: String,
    onDobChange: (String) -> Unit,
    ageValue: String,
    onAgeValueChange: (String) -> Unit,
    ageUnit: String,
    onAgeUnitChange: (String) -> Unit,
    gender: String,
    onGenderChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    isLoading: Boolean,
    onSave: () -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditMode) "Edit Patient" else "Add Patient") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(
                        value = clinicId,
                        onValueChange = onClinicIdChange,
                        label = "Patient ID (Optional)",
                        placeholder = "e.g. NEO-001",
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = "Full Name*",
                        placeholder = "Enter patient's name",
                        modifier = Modifier.weight(2f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = "Phone Number",
                        placeholder = "e.g., 9876543210",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = alternatePhone,
                        onValueChange = onAlternatePhoneChange,
                        label = "Alternate Phone",
                        placeholder = "(Optional)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }

                DateSelectionSection(
                    dob = dob,
                    onDobChange = onDobChange,
                    ageValue = ageValue,
                    onAgeValueChange = onAgeValueChange,
                    ageUnit = ageUnit,
                    onAgeUnitChange = onAgeUnitChange
                )

                GenderSelectionSection(
                    selectedGender = gender,
                    onGenderChange = onGenderChange
                )

                StandardTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = "Address (Optional)",
                    placeholder = "Full address",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                StandardButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isLoading
                ) {
                    Text(if (isEditMode) "Update Patient" else "Save Patient", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddPatientPreview() {
    NeoChildTheme {
        AddPatientContent(
            isEditMode = false,
            onBack = {},
            clinicId = "NEO-001",
            onClinicIdChange = {},
            name = "John Doe",
            onNameChange = {},
            phone = "1234567890",
            onPhoneChange = {},
            alternatePhone = "",
            onAlternatePhoneChange = {},
            dob = "1 Jan 2020",
            onDobChange = {},
            ageValue = "4",
            onAgeValueChange = {},
            ageUnit = "Years",
            onAgeUnitChange = {},
            gender = "Male",
            onGenderChange = {},
            address = "Sahibganj",
            onAddressChange = {},
            isLoading = false,
            onSave = {}
        )
    }
}
