package com.neochildclinic.feature.patient.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.designsystem.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatientContent(
    isEditMode: Boolean,
    snackbarHostState: SnackbarHostState,
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text(if (isEditMode) "Edit Patient" else "Add Patient") },
                    onBack = onBack
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .padding(Spacing.screen)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Section Title: Patient Identity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Patient identity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Required *",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StandardTextField(
                        value = clinicId,
                        onValueChange = onClinicIdChange,
                        label = "Patient ID (Optional)",
                        placeholder = "e.g. NEO-1042",
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = "Full Name *",
                        placeholder = "Enter patient's name",
                        modifier = Modifier.weight(2f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StandardTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = "Phone Number",
                        placeholder = "e.g., 98765 43210",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    StandardTextField(
                        value = alternatePhone,
                        onValueChange = onAlternatePhoneChange,
                        label = "Alternate Phone",
                        placeholder = "Optional",
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

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Section Title: Address
                Text(
                    text = "Address",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                StandardTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = "Address (Optional)",
                    placeholder = "Full residential address",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // Trust info note card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.cardDense),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                text = "Saved offline first",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Synced automatically when you reconnect.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                StandardButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = isLoading
                ) {
                    Text(
                        if (isEditMode) "Update Patient" else "Save Patient",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
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
            clinicId = "NEO-1042",
            onClinicIdChange = {},
            name = "Aarav Sharma",
            onNameChange = {},
            phone = "9876543210",
            onPhoneChange = {},
            alternatePhone = "",
            onAlternatePhoneChange = {},
            dob = "1 Jan 2022",
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
            snackbarHostState = remember { SnackbarHostState() },
            onSave = {}
        )
    }
}
