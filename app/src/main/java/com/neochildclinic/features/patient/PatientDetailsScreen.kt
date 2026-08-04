package com.neochildclinic.features.patient

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.core.ui.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailsScreen(
    patientId: String, 
    onBack: () -> Unit = {}, 
    onAddVaccine: (String) -> Unit = {},
    onAddConsultation: (String) -> Unit = {},
    onEditVaccination: (String) -> Unit = {},
    onEditPatient: (String) -> Unit = {},
    viewModel: PatientViewModel = hiltViewModel()
) {
    val allPatients by viewModel.allPatients.collectAsState()
    val patient = remember(patientId, allPatients) { allPatients.find { it.id == patientId } }
    val staff by viewModel.currentStaff.collectAsState()
    val isAdmin = staff?.role == "Admin"
    val canEditOrDelete = isAdmin || staff?.role == "Doctor"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                val fileName = it.lastPathSegment ?: "doc_${System.currentTimeMillis()}"
                viewModel.uploadDocument(patientId, fileName, bytes)
            }
        }
    }
    
    // Correct way to observe patient-specific history
    val patientVaccinations by viewModel.getPatientHistory(patientId).collectAsState(initial = emptyList())
    val patientConsultations by viewModel.getPatientConsultations(patientId).collectAsState(initial = emptyList())
    val documents by viewModel.documents.collectAsState()
    val followUps by viewModel.getPatientFollowUps(patientId).collectAsState(initial = emptyList())
    val patientNotes by viewModel.getPatientNotes(patientId).collectAsState(initial = emptyList())

    LaunchedEffect(patientId) {
        viewModel.loadDocuments(patientId)
    }

    var selectedSegment by remember { mutableIntStateOf(0) }
    var vaccinationToDelete by remember { mutableStateOf<Vaccination?>(null) }
    var patientToDelete by remember { mutableStateOf<com.neochildclinic.domain.model.Patient?>(null) }
    var showAuditLog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

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

    DeleteConfirmationDialog(
        show = patientToDelete != null,
        onDismiss = { patientToDelete = null },
        onConfirm = {
            val pId = patientToDelete?.id
            if (pId != null) {
                viewModel.deletePatient(pId) { success ->
                    if (success) {
                        Toast.makeText(context, "Patient record deleted", Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        Toast.makeText(context, "Failed to delete — please try again", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            patientToDelete = null
        },
        title = "Delete Patient",
        message = "Are you sure you want to delete this patient? All vaccination history will be lost."
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
                                if (canEditOrDelete) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Patient") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onEditPatient(patientId)
                                        }
                                    )
                                }
                                if (isAdmin) {
                                    DropdownMenuItem(
                                        text = { Text("Audit Log") },
                                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            showAuditLog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Patient", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuExpanded = false
                                            patientToDelete = patient
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
                Box(contentAlignment = Alignment.BottomEnd) {
                    FloatingActionButton(
                        onClick = { fabExpanded = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }

                    DropdownMenu(
                        expanded = fabExpanded,
                        onDismissRequest = { fabExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Vaccination") },
                            leadingIcon = { Icon(Icons.Default.Vaccines, contentDescription = null) },
                            onClick = {
                                fabExpanded = false
                                onAddVaccine(patientId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Consultation") },
                            leadingIcon = { Icon(Icons.Default.MedicalServices, contentDescription = null) },
                            onClick = {
                                fabExpanded = false
                                onAddConsultation(patientId)
                            }
                        )
                    }
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
                    consultations = patientConsultations,
                    documents = documents,
                    followUps = followUps,
                    notes = patientNotes,
                    canEditOrDelete = canEditOrDelete,
                    selectedSegment = selectedSegment,
                    onSegmentSelected = { selectedSegment = it },
                    onEdit_vaccination = onEditVaccination,
                    onDeleteVaccination = { vaccinationToDelete = it },
                    onUploadDocument = {
                        launcher.launch("*/*")
                    },
                    onDeleteDocument = { path ->
                        viewModel.deleteDocument(path, patientId)
                    },
                    onViewDocument = { path ->
                        scope.launch {
                            val url = viewModel.getDocumentUrl(path)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    viewModel = viewModel
                )
            }
        }
    }
}
