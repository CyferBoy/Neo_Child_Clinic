package com.clinic.neochild.features.patient

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.domain.model.Vaccination
import com.clinic.neochild.core.ui.*
import com.clinic.neochild.core.utils.PatientUtils.parseDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailsScreen(
    patientId: String, 
    onBack: () -> Unit = {}, 
    onAddVaccine: (String) -> Unit = {},
    onEditVaccination: (String) -> Unit = {},
    viewModel: PatientViewModel = hiltViewModel()
) {
    val allPatients by viewModel.allPatients.collectAsState()
    val patient = remember(patientId, allPatients) { allPatients.find { it.id == patientId } }
    val staff by viewModel.currentStaff.collectAsState()
    val isAdmin = staff?.role == "Admin"
    val canEditOrDelete = isAdmin || staff?.role == "Doctor"
    val context = LocalContext.current
    
    // Correct way to observe patient-specific history
    val patientVaccinations by viewModel.getPatientHistory(patientId).collectAsState(initial = emptyList())
    val followUps by viewModel.getPatientFollowUps(patientId).collectAsState(initial = emptyList())
    val patientNotes by viewModel.getPatientNotes(patientId).collectAsState(initial = emptyList())

    var vaccinationToDelete by remember { mutableStateOf<Vaccination?>(null) }
    var showAuditLog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    DeleteConfirmationDialog(
        show = vaccinationToDelete != null,
        onDismiss = { vaccinationToDelete = null },
        onConfirm = {
            val vId = vaccinationToDelete?.id
            if (vId != null) {
                viewModel.deleteVaccination(vId) { success ->
                    val msg = if (success) "Vaccination record deleted" else "Failed to delete — please try again"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            vaccinationToDelete = null
        },
        title = "Delete Vaccination",
        message = "Are you sure you want to delete this vaccination record?"
    )

    if (showAuditLog) {
        val auditLogs by viewModel.getAuditLogs(patientId).collectAsState(initial = emptyList())
        AuditLogDialog(
            show = showAuditLog,
            onDismiss = { showAuditLog = false },
            logs = auditLogs
        )
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Patient Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                if (isAdmin) {
                                    DropdownMenuItem(
                                        text = { Text("Audit Log") },
                                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            showAuditLog = true
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onAddVaccine(patientId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Vaccination")
                }
            }
        ) { paddingValues ->
            if (patient == null) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("Patient not found", style = MaterialTheme.typography.titleLarge)
                }
            } else {
                PatientDetailsContent(
                    paddingValues = paddingValues,
                    patient = patient,
                    vaccinations = patientVaccinations,
                    followUps = followUps,
                    notes = patientNotes,
                    canEditOrDelete = canEditOrDelete,
                    onEdit_vaccination = onEditVaccination,
                    onDeleteVaccination = { vaccinationToDelete = it },
                    viewModel = viewModel
                )
            }
        }
    }
}
