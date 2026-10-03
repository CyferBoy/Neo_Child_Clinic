package com.neochildclinic.feature.consultation.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.common.Constants
import com.neochildclinic.core.ui.*
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.Patient
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConsultationScreen(
    patientId: String,
    consultationId: String? = null,
    onBack: () -> Unit,
    viewModel: AddConsultationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
val snackbarHostState = remember { SnackbarHostState() }
val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern(Constants.DATE_FORMAT, Locale.ENGLISH)) }
    var date by rememberSaveable { mutableStateOf(today) }
    var cashAmount by rememberSaveable { mutableStateOf("") }
    var onlineAmount by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf("") }
    var nextFollowUpDate by rememberSaveable { mutableStateOf("") }
    var editFieldsLoaded by rememberSaveable { mutableStateOf(false) }
    // Validation errors live on the fields, not in Toasts: matches the inline
    // isError convention DoctorDropdown/AvailableSlotDropdown already use here,
    // and stays visible where the input is.
    var problemError by rememberSaveable { mutableStateOf<String?>(null) }
    var feeError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(patientId, consultationId) {
        if (consultationId.isNullOrBlank()) {
            viewModel.loadPatient(patientId)
        } else {
            viewModel.loadForEdit(consultationId)
        }
    }

    // Recompute available slots whenever the selected doctor or date changes (req. 1/22),
    // clearing any slot that belonged to a different doctor/date along the way.
    LaunchedEffect(uiState.selectedDoctor, date) {
        viewModel.loadAvailableSlots(date)
    }

    LaunchedEffect(uiState.editingConsultation) {
        val consultation = uiState.editingConsultation
        if (consultation != null && !editFieldsLoaded) {
            date = consultation.date
            cashAmount = consultation.cashAmount.toString()
            onlineAmount = consultation.onlineAmount.toString()
            problem = consultation.problem
            nextFollowUpDate = consultation.nextFollowUpDate
            editFieldsLoaded = true
        }
    }

    val totalAmount = (cashAmount.toDoubleOrNull() ?: 0.0) + (onlineAmount.toDoubleOrNull() ?: 0.0)
    // Was "₹$totalAmount", which rendered "₹500.0". Also truncating with toInt()
    // would silently drop a .50 fee, so show decimals only when they exist.
    val totalDisplay = if (totalAmount % 1.0 == 0.0) totalAmount.toLong().toString()
                        else "%.2f".format(totalAmount)

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            scope.launch { snackbarHostState.showSnackbar(if (consultationId.isNullOrBlank()) "Consultation saved" else "Consultation updated") }
            viewModel.resetState()
            onBack()
        }
    }

    ShowSnackbar(uiState.error, snackbarHostState) { viewModel.resetState() }

    AppBackground {
        Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text(if (consultationId.isNullOrBlank()) "Add Consultation" else "Edit Consultation") },
                    onBack = onBack
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = Elevation.sheet,
                    shadowElevation = Elevation.sheet
                ) {
                    StandardButton(
                        onClick = {
                            problemError = if (problem.isBlank()) "Enter the problem or chief complaint" else null
                            feeError = if (totalAmount <= 0) "Enter a consultation fee (cash or online)" else null
                            if (problemError != null || feeError != null) return@StandardButton
                            viewModel.saveConsultation(
                                patientId = patientId,
                                date = date,
                                cash = cashAmount.toDoubleOrNull() ?: 0.0,
                                online = onlineAmount.toDoubleOrNull() ?: 0.0,
                                problem = problem,
                                nextFollowUpDate = nextFollowUpDate
                            )
                        },
                        isLoading = uiState.isLoading,
                        modifier = Modifier.padding(Spacing.lg).fillMaxWidth()
                    ) {
                        Text(if (consultationId.isNullOrBlank()) "Save Consultation" else "Save Changes", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = Spacing.lg)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Spacer(Modifier.height(Spacing.lg))

                // Patient Info (Read Only)
                uiState.patient?.let { PatientSummaryCard(it) }

                SectionHeader("Consultation Details")

                DateDropdownPicker(
                    label = "Consultation Date*",
                    currentDate = date,
                    onDateSelected = { date = it }
                )

                DoctorDropdown(
                    doctors = uiState.allDoctors,
                    selectedDoctor = uiState.selectedDoctor,
                    onDoctorSelected = { viewModel.selectDoctor(it) },
                    isError = uiState.doctorError
                )

                AvailableSlotDropdown(
                    state = uiState.slotsState,
                    selectedSlot = uiState.selectedSlot,
                    onSlotSelected = { viewModel.selectSlot(it) },
                    isError = uiState.slotError
                )

                StandardTextField(
                    value = problem,
                    onValueChange = { problem = it; if (problemError != null) problemError = null },
                    label = "Problem / Chief Complaint*",
                    placeholder = "e.g. Fever, Cough, Routine Check-up",
                    minLines = 3,
                    isError = problemError != null,
                    errorText = problemError
                )

                SectionHeader("Payment")

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StandardTextField(
                        value = cashAmount,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) { cashAmount = it; feeError = null } },
                        label = "Cash",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        placeholder = "0"
                    )
                    StandardTextField(
                        value = onlineAmount,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) { onlineAmount = it; feeError = null } },
                        label = "Online",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        placeholder = "0"
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (feeError != null) MaterialTheme.colorScheme.errorContainer
                                         else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.card)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Amount (Read Only)", style = MaterialTheme.typography.titleMedium)
                            Text("₹$totalDisplay", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        if (feeError != null) {
                            Text(
                                text = feeError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                SectionHeader("Next Visit")

                DateDropdownPicker(
                    label = "Next Follow-up Date",
                    currentDate = nextFollowUpDate,
                    onDateSelected = { nextFollowUpDate = it }
                )

                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun PatientSummaryCard(patient: Patient) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(patient.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val clinicId = if (patient.patientClinicId?.startsWith("TEMP-") == true || patient.patientClinicId.isNullOrBlank()) "Not Assigned" else patient.patientClinicId ?: ""
            Text("ID: $clinicId", style = MaterialTheme.typography.bodyMedium)
            Text("${patient.gender} | DOB: ${patient.dob}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = Spacing.sm),
        color = MaterialTheme.colorScheme.primary
    )
}
