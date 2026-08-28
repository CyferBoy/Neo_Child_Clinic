package com.neochildclinic.features.vaccination

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.neochildclinic.domain.model.Vaccine
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.StandardTextField
import com.neochildclinic.core.ui.DateDropdownPicker
import com.neochildclinic.core.designsystem.NeoChildTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVaccinationContent(
    isEdit: Boolean,
    onBack: () -> Unit,
    patientId: String,
    onPatientIdChange: (String) -> Unit,
    isPatientIdEnabled: Boolean,
    inventory: List<Vaccine>,
    selectedVaccines: List<String>,
    onVaccineSelected: (Vaccine) -> Unit,
    onRemoveVaccine: (Int) -> Unit,
    nextBrandSearch: String,
    onNextBrandChange: (String) -> Unit,
    dateGiven: String,
    onDateGivenChange: (String) -> Unit,
    nextDueDate: String,
    onNextDueDateChange: (String) -> Unit,
    cashAmount: String,
    onCashChange: (String) -> Unit,
    onlineAmount: String,
    onOnlineChange: (String) -> Unit,
    totalPaid: Double,
    cost: String,
    onCostChange: (String) -> Unit,
    withFees: Boolean,
    onFeesToggle: (Boolean) -> Unit,
    doctorsAcc: Boolean,
    onAccToggle: (Boolean) -> Unit,
    isLoading: Boolean,
    onSave: () -> Unit,
    onSaveAndDownload: () -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (isEdit) "Edit Vaccination" else "Add Vaccination") },
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
                StandardTextField(
                    value = patientId,
                    onValueChange = onPatientIdChange,
                    label = "Patient ID*",
                    placeholder = "Enter patient ID",
                    enabled = isPatientIdEnabled
                )

                VaccineSelectionSection(
                    inventory = inventory,
                    selectedVaccines = selectedVaccines,
                    onVaccineSelected = onVaccineSelected,
                    onRemoveVaccine = onRemoveVaccine
                )

                StandardTextField(
                    value = nextBrandSearch,
                    onValueChange = onNextBrandChange,
                    label = "Next Vaccine",
                    placeholder = "Enter next vaccine name"
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateDropdownPicker(label = "Date Given*", currentDate = dateGiven, onDateSelected = onDateGivenChange, modifier = Modifier.weight(1f))
                    DateDropdownPicker(label = "Next Due Date", currentDate = nextDueDate, onDateSelected = onNextDueDateChange, modifier = Modifier.weight(1f))
                }

                PaymentSection(
                    cash = cashAmount,
                    online = onlineAmount,
                    total = totalPaid,
                    cost = cost,
                    withFees = withFees,
                    doctorsAcc = doctorsAcc,
                    onCashChange = onCashChange,
                    onOnlineChange = onOnlineChange,
                    onCostChange = onCostChange,
                    onFeesToggle = onFeesToggle,
                    onAccToggle = onAccToggle
                )

                Spacer(modifier = Modifier.height(16.dp))

                ActionButtons(
                    isLoading = isLoading,
                    isEdit = isEdit,
                    onSave = onSave,
                    onSaveAndDownload = onSaveAndDownload
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddVaccinationPreview() {
    NeoChildTheme {
        AddVaccinationContent(
            isEdit = false,
            onBack = {},
            patientId = "P001",
            onPatientIdChange = {},
            isPatientIdEnabled = true,
            inventory = emptyList(),
            selectedVaccines = listOf("BCG", "HepB"),
            onVaccineSelected = {},
            onRemoveVaccine = {},
            nextBrandSearch = "",
            onNextBrandChange = {},
            dateGiven = "1 Jan 2024",
            onDateGivenChange = {},
            nextDueDate = "1 Feb 2024",
            onNextDueDateChange = {},
            cashAmount = "500",
            onCashChange = {},
            onlineAmount = "0",
            onOnlineChange = {},
            totalPaid = 500.0,
            cost = "500",
            onCostChange = {},
            withFees = false,
            onFeesToggle = {},
            doctorsAcc = false,
            onAccToggle = {},
            isLoading = false,
            onSave = {},
            onSaveAndDownload = {}
        )
    }
}
