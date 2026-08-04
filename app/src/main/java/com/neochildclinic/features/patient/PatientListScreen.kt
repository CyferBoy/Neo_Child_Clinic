package com.neochildclinic.features.patient

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.DeleteConfirmationDialog
import com.neochildclinic.core.ui.SearchTopAppBar
import com.neochildclinic.core.ui.ActionDropdownMenu
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.utils.PatientUtils.calculateAgeLabel

@Composable
fun PatientListScreen(
    onBack: () -> Unit = {},
    onAddPatient: () -> Unit = {},
    onPatientClick: (String) -> Unit = {},
    onEditPatient: (String) -> Unit = {},
    viewModel: PatientListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val staff by viewModel.currentStaff.collectAsState()
    val isAdmin = staff?.role == "Admin"
    val canEditOrDelete = isAdmin || staff?.role == "Doctor"
    val context = LocalContext.current
    
    var patientToDelete by remember { mutableStateOf<Patient?>(null) }
    var patientForAuditLog by remember { mutableStateOf<Patient?>(null) }
    var showManualMergeDialog by rememberSaveable { mutableStateOf(false) }

    // Error handling
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    DeleteConfirmationDialog(
        show = patientToDelete != null,
        onDismiss = { patientToDelete = null },
        onConfirm = {
            patientToDelete?.let { viewModel.deletePatient(it.id) }
            patientToDelete = null
        },
        title = "Delete Patient",
        message = "Are you sure you want to delete ${patientToDelete?.name}? This will remove all their records."
    )

    if (patientForAuditLog != null) {
        val auditLogs by viewModel.getAuditLogs(patientForAuditLog!!.id).collectAsState(initial = emptyList())
        AuditLogDialog(
            show = true,
            onDismiss = { patientForAuditLog = null },
            logs = auditLogs
        )
    }

    if (showManualMergeDialog && uiState.selectedPatients.size == 2) {
        ManualMergeDialog(
            selectedPatients = uiState.selectedPatients.toList(),
            isMerging = uiState.isMerging,
            onDismiss = { showManualMergeDialog = false },
            onConfirm = { master ->
                viewModel.mergeSelectedPatients(master)
                showManualMergeDialog = false
            }
        )
    }

    PatientListContent(
        uiState = uiState,
        isAdmin = isAdmin,
        canEditOrDelete = canEditOrDelete,
        onBack = {
            if (uiState.isMergeSelectionMode) viewModel.clearSelection()
            else onBack()
        },
        onRefresh = viewModel::refresh,
        onAddPatient = onAddPatient,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onMergeClick = { showManualMergeDialog = true },
        onPatientClick = { patient ->
            if (uiState.isMergeSelectionMode) viewModel.toggleSelection(patient)
            else onPatientClick(patient.id)
        },
        onPatientLongClick = { patient ->
            if (!uiState.isMergeSelectionMode) viewModel.enterMergeMode(patient)
        },
        onEditPatient = onEditPatient,
        onDeletePatient = { patientToDelete = it },
        onToggleSelection = viewModel::toggleSelection,
        onViewAuditLog = { patientForAuditLog = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientListContent(
    uiState: PatientListUiState,
    isAdmin: Boolean,
    canEditOrDelete: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddPatient: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMergeClick: () -> Unit,
    onPatientClick: (Patient) -> Unit,
    onPatientLongClick: (Patient) -> Unit,
    onEditPatient: (String) -> Unit,
    onDeletePatient: (Patient) -> Unit,
    onToggleSelection: (Patient) -> Unit,
    onViewAuditLog: (Patient) -> Unit
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                PatientListTopBar(
                    uiState = uiState,
                    onSearchQueryChange = onSearchQueryChange,
                    onBack = onBack,
                    onMergeClick = onMergeClick
                )
            },
            floatingActionButton = {
                if (!uiState.isMergeSelectionMode) {
                    FloatingActionButton(
                        onClick = onAddPatient,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Patient")
                    }
                }
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.patients.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No patients found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (uiState.totalCount > 0) {
                                Text(
                                    "(${uiState.totalCount} records are archived or hidden)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = 88.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.patients, key = { it.id }) { patient ->
                            PatientCard(
                                patient = patient,
                                isSelected = uiState.selectedPatients.contains(patient),
                                isMergeMode = uiState.isMergeSelectionMode,
                                isAdmin = isAdmin,
                                canEditOrDelete = canEditOrDelete,
                                hasMissingPrice = uiState.patientsWithMissingPrice.contains(patient.id),
                                onClick = { onPatientClick(patient) },
                                onLongClick = { onPatientLongClick(patient) },
                                onEdit = { onEditPatient(patient.id) },
                                onDelete = { onDeletePatient(patient) },
                                onToggleSelection = { onToggleSelection(patient) },
                                onViewAuditLog = { onViewAuditLog(patient) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientListTopBar(
    uiState: PatientListUiState,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onMergeClick: () -> Unit
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    SearchTopAppBar(
        title = if (uiState.isMergeSelectionMode) "${uiState.selectedPatients.size} Selected" else "Patients",
        searchQuery = uiState.searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        isSearchActive = isSearchActive,
        onSearchActiveChange = { isSearchActive = it },
        onBack = onBack,
        actions = {
            if (uiState.selectedPatients.size == 2) {
                IconButton(onClick = onMergeClick) {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = "Merge Selected", tint = Color.Yellow)
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PatientCard(
    patient: Patient,
    isSelected: Boolean,
    isMergeMode: Boolean,
    isAdmin: Boolean,
    canEditOrDelete: Boolean,
    hasMissingPrice: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelection: () -> Unit,
    onViewAuditLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMergeMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            PatientAvatar(name = patient.name, isSelected = isSelected)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = patient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val clinicIdDisplay = if (patient.patientClinicId.isBlank() || patient.patientClinicId.startsWith("TEMP-")) "Not Assigned" else patient.patientClinicId
                Text(text = "ID: $clinicIdDisplay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                PatientInfoSubtitle(dob = patient.dob, gender = patient.gender)
            }

            if (hasMissingPrice && !isMergeMode) {
                Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (!isMergeMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                    }
                    ActionDropdownMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onMerge = onLongClick,
                        isAdmin = canEditOrDelete,
                        onAuditLog = if (isAdmin) onViewAuditLog else null
                    )
                }
            }
        }
    }
}

@Composable
private fun PatientAvatar(name: String, isSelected: Boolean) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PatientInfoSubtitle(dob: String, gender: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val ageLabel = remember(dob) { if (dob.isNotBlank()) calculateAgeLabel(dob) else null }
        if (ageLabel != null) {
            Text(text = ageLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(text = " • ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Text(text = gender.ifEmpty { "Unknown" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ManualMergeDialog(
    selectedPatients: List<Patient>,
    isMerging: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Patient) -> Unit
) {
    var mergeMasterPatient by remember { mutableStateOf<Patient?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isMerging) onDismiss() },
        title = { Text("Manual Merge") },
        text = {
            Column {
                Text("Select the patient profile you want to KEEP. The other will be deleted and its vaccinations moved.")
                Spacer(modifier = Modifier.height(16.dp))
                selectedPatients.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().combinedClickable { mergeMasterPatient = p }.padding(8.dp)
                    ) {
                        RadioButton(selected = mergeMasterPatient == p, onClick = { mergeMasterPatient = p })
                        Text("${p.name} (${p.id})")
                    }
                }
            }
        },
        confirmButton = {
            StandardButton(
                onClick = { mergeMasterPatient?.let { onConfirm(it) } },
                enabled = mergeMasterPatient != null && !isMerging,
                isLoading = isMerging
            ) {
                Text("Confirm Merge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isMerging) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PatientListPreview() {
    NeoChildTheme {
        PatientListContent(
            uiState = PatientListUiState(
                patients = listOf(
                    Patient("1", "John Doe", "1234567890", "", "2020-01-01", "Male", "Address", "2024-01-01"),
                    Patient("2", "Jane Smith", "0987654321", "", "2021-05-15", "Female", "", "2024-02-10")
                )
            ),
            isAdmin = true,
            canEditOrDelete = true,
            onBack = {},
            onAddPatient = {},
            onSearchQueryChange = {},
            onMergeClick = {},
            onPatientClick = {},
            onPatientLongClick = {},
            onEditPatient = {},
            onDeletePatient = {},
            onToggleSelection = {},
            onViewAuditLog = {},
            onRefresh = {}
        )
    }
}
