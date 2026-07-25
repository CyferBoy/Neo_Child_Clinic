package com.clinic.neochild.features.vaccination

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.clinic.neochild.core.utils.InventoryUtils
import com.clinic.neochild.domain.model.Vaccine
import com.clinic.neochild.features.patient.PatientViewModel
import com.clinic.neochild.core.constants.Constants
import com.clinic.neochild.core.utils.ReceiptManager
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddVaccinationScreen(
    initialPatientId: String = "", 
    initialVaccineName: String? = null,
    vaccinationId: String? = null,
    onBack: () -> Unit = {},
    patientViewModel: PatientViewModel = hiltViewModel(),
    viewModel: AddVaccinationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val allPatients by patientViewModel.allPatients.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Initialize initial data
    LaunchedEffect(initialPatientId) {
        if (initialPatientId.isNotEmpty()) {
            viewModel.onPatientIdChange(initialPatientId)
        }
    }

    LaunchedEffect(vaccinationId) {
        if (vaccinationId != null) {
            viewModel.loadVaccination(vaccinationId)
        }
    }

    val currentVaccination by viewModel.vaccination.collectAsState()
    LaunchedEffect(currentVaccination) {
        currentVaccination?.let { viewModel.prefillForm(it) }
    }

    LaunchedEffect(Unit) {
        val today = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date())
        viewModel.initializeDates(today)
    }

    // Auto-trigger selection if initial vaccine provided
    LaunchedEffect(initialVaccineName, uiState.availableVaccines) {
        if (vaccinationId == null && initialVaccineName != null && uiState.selectedVaccines.isEmpty() && uiState.availableVaccines.isNotEmpty()) {
            val matching = uiState.availableVaccines.find { it.brandName.equals(initialVaccineName, ignoreCase = true) }
            if (matching != null) {
                viewModel.onVaccineSelected(matching)
            }
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.resetSaveState()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    uiState.vaccineRequiringBatchSelection?.let { vaccine ->
        val batches = uiState.activeBatches[vaccine.id] ?: emptyList()
        AlertDialog(
            onDismissRequest = { viewModel.dismissBatchSelection() },
            title = { Text("Select Batch for ${vaccine.brandName}") },
            text = {
                Column {
                    Text("Select an active batch. FEFO prioritized.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    batches.forEach { batch ->
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("Batch: ${batch.batchNumber}", fontWeight = FontWeight.Bold)
                                    Text("Expires: ${batch.expiryDate} | Stock: ${batch.remainingQuantity}", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = { viewModel.onBatchSelected(vaccine, batch) }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBatchSelection() }) { Text("Cancel") }
            }
        )
    }

    AddVaccinationContent(
        isEdit = vaccinationId != null,
        onBack = onBack,
        patientId = uiState.patientId,
        onPatientIdChange = viewModel::onPatientIdChange,
        isPatientIdEnabled = initialPatientId.isEmpty(),
        inventory = uiState.availableVaccines,
        selectedVaccines = uiState.selectedVaccines,
        onVaccineSelected = { v ->
            if (!uiState.selectedVaccines.contains(v.brandName)) {
                viewModel.onVaccineSelected(v)
            }
        },
        onRemoveVaccine = viewModel::onRemoveVaccine,
        nextBrandSearch = uiState.nextBrandSearch,
        onNextBrandChange = viewModel::onNextBrandChange,
        dateGiven = uiState.dateGiven,
        onDateGivenChange = viewModel::onDateGivenChange,
        nextDueDate = uiState.nextDueDate,
        onNextDueDateChange = viewModel::onNextDueDateChange,
        cashAmount = uiState.cashAmount,
        onCashChange = viewModel::onCashChange,
        onlineAmount = uiState.onlineAmount,
        onOnlineChange = viewModel::onOnlineChange,
        totalPaid = uiState.totalPaid,
        cost = uiState.cost,
        onCostChange = viewModel::onCostChange,
        withFees = uiState.withFees,
        onFeesToggle = viewModel::onFeesToggle,
        doctorsAcc = uiState.doctorsAcc,
        onAccToggle = viewModel::onAccToggle,
        isLoading = uiState.isLoading,
        onSave = {
            if (VaccinationValidator.validateForm(context, uiState.patientId, uiState.selectedVaccines)) {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                val v = VaccinationValidator.createVaccination(
                    vaccinationId, uiState.patientId, uiState.selectedVaccines, uiState.selectedVaccineIds, 
                    uiState.nextBrandSearch, uiState.dateGiven, uiState.nextDueDate, uiState.cost, 
                    uiState.cashAmount, uiState.onlineAmount, uiState.totalPaid, uiState.withFees, 
                    uiState.doctorsAcc, uiState.batchNumbers, uiState.expiryDates, user, uiState.receiptNumber
                )
                viewModel.saveVaccination(v, vaccinationId == null, uiState.selectedVaccineIds, uiState.selectedBatchIds) {}
            }
        },
        onSaveAndDownload = {
            val patient = allPatients.find { it.id == uiState.patientId }
            if (VaccinationValidator.validateForm(context, uiState.patientId, uiState.selectedVaccines) && patient != null) {
                val user = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
                val v = VaccinationValidator.createVaccination(
                    vaccinationId, uiState.patientId, uiState.selectedVaccines, uiState.selectedVaccineIds, 
                    uiState.nextBrandSearch, uiState.dateGiven, uiState.nextDueDate, uiState.cost, 
                    uiState.cashAmount, uiState.onlineAmount, uiState.totalPaid, uiState.withFees, 
                    uiState.doctorsAcc, uiState.batchNumbers, uiState.expiryDates, user, uiState.receiptNumber
                )
                viewModel.saveVaccination(v, vaccinationId == null, uiState.selectedVaccineIds, uiState.selectedBatchIds) {
                    scope.launch {
                        ReceiptManager.downloadReceipt(context, patient, v)
                    }
                }
            } else if (patient == null) {
                Toast.makeText(context, "Patient not found", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
