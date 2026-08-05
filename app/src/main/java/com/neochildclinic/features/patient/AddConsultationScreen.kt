package com.neochildclinic.features.patient

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.neochildclinic.core.constants.Constants
import com.neochildclinic.core.ui.*
import com.neochildclinic.domain.model.Patient
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConsultationScreen(
    patientId: String,
    onBack: () -> Unit,
    viewModel: AddConsultationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    val today = remember { SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(Date()) }
    var date by rememberSaveable { mutableStateOf(today) }
    var doctorName by remember { mutableStateOf("") }
    var cashAmount by rememberSaveable { mutableStateOf("") }
    var onlineAmount by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf("") }
    var nextFollowUpDate by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(patientId) {
        viewModel.loadPatient(patientId)
    }

    LaunchedEffect(uiState.doctorProfile) {
        uiState.doctorProfile?.let {
            doctorName = it.displayName
        }
    }

    val totalAmount = (cashAmount.toDoubleOrNull() ?: 0.0) + (onlineAmount.toDoubleOrNull() ?: 0.0)

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            Toast.makeText(context, "Consultation saved", Toast.LENGTH_SHORT).show()
            viewModel.resetState()
            onBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.resetState()
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Add Consultation") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    StandardButton(
                        onClick = {
                            if (problem.isBlank()) {
                                Toast.makeText(context, "Please enter problem / complaint", Toast.LENGTH_SHORT).show()
                                return@StandardButton
                            }
                            if (totalAmount <= 0) {
                                Toast.makeText(context, "Please enter consultation fee", Toast.LENGTH_SHORT).show()
                                return@StandardButton
                            }
                            viewModel.saveConsultation(
                                patientId = patientId,
                                doctorName = doctorName,
                                date = date,
                                cash = cashAmount.toDoubleOrNull() ?: 0.0,
                                online = onlineAmount.toDoubleOrNull() ?: 0.0,
                                problem = problem,
                                nextFollowUpDate = nextFollowUpDate
                            )
                        },
                        isLoading = uiState.isLoading,
                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                    ) {
                        Text("Save Consultation", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // Patient Info (Read Only)
                uiState.patient?.let { PatientSummaryCard(it) }

                SectionHeader("Consultation Details")

                DateDropdownPicker(
                    label = "Consultation Date*",
                    currentDate = date,
                    onDateSelected = { date = it }
                )

                StandardTextField(
                    value = doctorName,
                    onValueChange = { doctorName = it },
                    label = "Doctor*",
                    placeholder = "Doctor Name"
                )

                StandardTextField(
                    value = problem,
                    onValueChange = { problem = it },
                    label = "Problem / Chief Complaint*",
                    placeholder = "e.g. Fever, Cough, Routine Check-up",
                    minLines = 3
                )

                SectionHeader("Payment")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StandardTextField(
                        value = cashAmount,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) cashAmount = it },
                        label = "Cash",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        placeholder = "0"
                    )
                    StandardTextField(
                        value = onlineAmount,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) onlineAmount = it },
                        label = "Online",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        placeholder = "0"
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Amount (Read Only)", style = MaterialTheme.typography.titleMedium)
                        Text("₹$totalAmount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(patient.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val clinicId = if (patient.patientClinicId.startsWith("TEMP-") || patient.patientClinicId.isBlank()) "Not Assigned" else patient.patientClinicId
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
        modifier = Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.primary
    )
}
