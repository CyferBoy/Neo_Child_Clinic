package com.neochildclinic.features.patient

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import com.neochildclinic.domain.model.UserRole
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
    onEditConsultation: (String) -> Unit = {},
    onEditVaccination: (String) -> Unit = {},
    onViewVaccination: (String) -> Unit = {},
    onEditPatient: (String) -> Unit = {},
    viewModel: PatientViewModel = hiltViewModel()
) {
    val allPatients by viewModel.allPatients.collectAsState()
    val patient = remember(patientId, allPatients) { allPatients.find { it.id == patientId } }
    val profile by viewModel.currentProfile.collectAsState()
    val isAdmin = profile?.role == UserRole.admin
    val canEditOrDelete = isAdmin || profile?.role == UserRole.doctor
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
    
    val patientVaccinationCards by viewModel.getPatientVaccinationCards(patientId).collectAsState(initial = null)
    val patientConsultations by viewModel.getPatientConsultations(patientId).collectAsState(initial = emptyList())
    val documents by viewModel.documents.collectAsState()
    val patientNotes by viewModel.getPatientNotes(patientId).collectAsState(initial = emptyList())
    val doctorMap by viewModel.doctorMap.collectAsState()
    val vaccineMap by viewModel.vaccineMap.collectAsState()

    LaunchedEffect(patientId) {
        viewModel.loadDocuments(patientId)
    }

    var selectedSegment by remember { mutableIntStateOf(0) }
    var vaccinationToDelete by remember { mutableStateOf<Vaccination?>(null) }
    var consultationToDelete by remember { mutableStateOf<com.neochildclinic.domain.model.Consultation?>(null) }
    var patientToDelete by remember { mutableStateOf<com.neochildclinic.domain.model.Patient?>(null) }
    var showAuditLog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    var selectedVaccinationForAction by remember { mutableStateOf<Vaccination?>(null) }
    var selectedConsultationForAction by remember { mutableStateOf<com.neochildclinic.domain.model.Consultation?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    DeleteConfirmationDialog(
        show = vaccinationToDelete != null,
        onDismiss = { vaccinationToDelete = null },
        onConfirm = {
            val vId = vaccinationToDelete?.id
            if (vId != null) {
                viewModel.deleteVaccination(vId) { success ->
                    val msg = if (success) "Vaccination record deleted" else "Failed to delete"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            vaccinationToDelete = null
        },
        title = "Delete Vaccination",
        message = "Are you sure you want to delete this vaccination record?"
    )

    DeleteConfirmationDialog(
        show = consultationToDelete != null,
        onDismiss = { consultationToDelete = null },
        onConfirm = {
            val cId = consultationToDelete?.id
            if (cId != null) {
                viewModel.deleteConsultation(cId) { success ->
                    val msg = if (success) "Consultation record deleted" else "Failed to delete"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
            consultationToDelete = null
        },
        title = "Delete Consultation",
        message = "Are you sure you want to delete this consultation record?"
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
                        Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
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

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = if (selectedVaccinationForAction != null) "Vaccination Actions" else "Consultation Actions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (selectedVaccinationForAction != null) {
                    ListItem(
                        headlineContent = { Text("Print Receipt") },
                        leadingContent = { Icon(Icons.Default.Print, null) },
                        modifier = Modifier.clickable {
                            showSheet = false
                            val doctorName = doctorMap[selectedVaccinationForAction!!.doctorId] ?: selectedVaccinationForAction!!.performedBy
                            com.neochildclinic.core.utils.ReceiptManager.printReceipt(context, patient!!, selectedVaccinationForAction!!, doctorName)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Download Receipt") },
                        leadingContent = { Icon(Icons.Default.Download, null) },
                        modifier = Modifier.clickable {
                            showSheet = false
                            scope.launch {
                                val doctorName = doctorMap[selectedVaccinationForAction!!.doctorId] ?: selectedVaccinationForAction!!.performedBy
                                com.neochildclinic.core.utils.ReceiptManager.downloadReceipt(context, patient!!, selectedVaccinationForAction!!, doctorName)
                            }
                        }
                    )
                    if (canEditOrDelete) {
                        ListItem(
                            headlineContent = { Text("Edit Record") },
                            leadingContent = { Icon(Icons.Default.Edit, null) },
                            modifier = Modifier.clickable {
                                showSheet = false
                                onEditVaccination(selectedVaccinationForAction!!.id)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Delete Record", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable {
                                showSheet = false
                                vaccinationToDelete = selectedVaccinationForAction
                            }
                        )
                    }
                    ListItem(
                        headlineContent = { Text("View Audit History") },
                        leadingContent = { Icon(Icons.Default.History, null) },
                        modifier = Modifier.clickable {
                            showSheet = false
                            // Assuming we can use showAuditLog for individual records or patient
                            // For now, mirroring the existing behavior
                            showAuditLog = true
                        }
                    )
                } else if (selectedConsultationForAction != null) {
                    if (canEditOrDelete) {
                        ListItem(
                            headlineContent = { Text("Edit Record") },
                            leadingContent = { Icon(Icons.Default.Edit, null) },
                            modifier = Modifier.clickable {
                                showSheet = false
                                onEditConsultation(selectedConsultationForAction!!.id)
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Delete Record", color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable {
                                showSheet = false
                                consultationToDelete = selectedConsultationForAction
                            }
                        )
                    }
                }
            }
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Patient Details") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                if (canEditOrDelete) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Patient") },
                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                        onClick = {
                                            menuExpanded = false
                                            onEditPatient(patientId)
                                        }
                                    )
                                }
                                if (isAdmin) {
                                    DropdownMenuItem(
                                        text = { Text("View Audit History") },
                                        leadingIcon = { Icon(Icons.Default.History, null) },
                                        onClick = {
                                            menuExpanded = false
                                            showAuditLog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Patient", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
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
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
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
                        Icon(Icons.Default.Add, "Add")
                    }

                    DropdownMenu(
                        expanded = fabExpanded,
                        onDismissRequest = { fabExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Vaccination") },
                            leadingIcon = { Icon(Icons.Default.Vaccines, null) },
                            onClick = {
                                fabExpanded = false
                                onAddVaccine(patientId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Consultation") },
                            leadingIcon = { Icon(Icons.Default.MedicalServices, null) },
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
                    vaccinations = patientVaccinationCards?.map { it.vaccination }.orEmpty(),
                    vaccinationCardData = patientVaccinationCards,
                    consultations = patientConsultations,
                    documents = documents,
                    notes = patientNotes,
                    doctorMap = doctorMap,
                    vaccineMap = vaccineMap,
                    canEditOrDelete = canEditOrDelete,
                    selectedSegment = selectedSegment,
                    onSegmentSelected = { selectedSegment = it },
                    onLongClickVaccination = { 
                        selectedVaccinationForAction = it
                        selectedConsultationForAction = null
                        showSheet = true
                    },
                    onLongClickConsultation = { 
                        selectedConsultationForAction = it
                        selectedVaccinationForAction = null
                        showSheet = true
                    },
                    onOpenVaccinationDetails = { onViewVaccination(it.id) },
                    onUploadDocument = { launcher.launch("*/*") },
                    onDeleteDocument = { viewModel.deleteDocument(it, patientId) },
                    onViewDocument = { path ->
                        scope.launch {
                            val url = viewModel.getDocumentUrl(path)
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    viewModel = viewModel
                )
            }
        }
    }
}
