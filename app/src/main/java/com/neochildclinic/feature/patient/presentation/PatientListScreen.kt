package com.neochildclinic.feature.patient.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.ShowSnackbar
import com.neochildclinic.core.ui.StandardButton
import com.neochildclinic.core.ui.DeleteConfirmationDialog
import com.neochildclinic.core.ui.SearchTopAppBar
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.ui.EmptyState
import com.neochildclinic.core.ui.ErrorState
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.common.PatientUtils.calculateAgeLabel

@Composable
fun PatientListScreen(
    onBack: () -> Unit = {},
    onAddPatient: () -> Unit = {},
    onPatientClick: (String) -> Unit = {},
    onEditPatient: (String) -> Unit = {},
    viewModel: PatientListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val role = viewModel.currentUserRole
    val isAdmin = role == UserRole.admin
    val canEditOrDelete = isAdmin || role == UserRole.doctor
    
    var patientToDelete by remember { mutableStateOf<Patient?>(null) }
    var showManualMergeDialog by rememberSaveable { mutableStateOf(false) }

    ShowSnackbar(uiState.error, snackbarHostState) { viewModel.clearError() }

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
        snackbarHostState = snackbarHostState,
        searchQuery = searchQuery,
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
        onToggleSelection = viewModel::toggleSelection
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientListContent(
    uiState: PatientListUiState,
    snackbarHostState: SnackbarHostState,
    searchQuery: String,
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
    onToggleSelection: (Patient) -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PatientListTopBar(
                uiState = uiState,
                searchQuery = searchQuery,
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
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
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
                SkeletonList(
                    modifier = Modifier.fillMaxSize(),
                    count = 8,
                    cardShaped = true,
                    spacing = Spacing.sm,
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm)
                )
            } else if (uiState.patients.isEmpty() && uiState.error != null) {
                ErrorState(
                    modifier = Modifier.fillMaxSize(),
                    message = uiState.error,
                    onRetry = onRefresh
                )
            } else if (uiState.patients.isEmpty()) {
                EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Default.People,
                    title = "No patients found",
                    message = if (uiState.totalCount > 0)
                        "${uiState.totalCount} records are archived or hidden by the current filters."
                    else "Add the first patient to start recording vaccinations.",
                    actionLabel = if (uiState.totalCount > 0) null else "Add patient",
                    onAction = onAddPatient
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.screen),
                    contentPadding = PaddingValues(bottom = 88.dp, top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // Header counter item
                    item {
                        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                            Text(
                                text = "${uiState.patients.size} patients",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select a patient to manage medical hub or vaccines",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
                            modifier = Modifier.animateItem()
                        )
                    }

                    // Footer note matching patients_list_light.svg (y=764)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.md),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Showing ${uiState.patients.size} of ${uiState.totalCount.coerceAtLeast(uiState.patients.size)} · pull to refresh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onMergeClick: () -> Unit
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    SearchTopAppBar(
        title = if (uiState.isMergeSelectionMode) "${uiState.selectedPatients.size} Selected" else "Patients",
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        isSearchActive = isSearchActive,
        onSearchActiveChange = { isSearchActive = it },
        onBack = onBack,
        actions = {
            if (uiState.selectedPatients.size == 2) {
                IconButton(onClick = onMergeClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallMerge,
                        contentDescription = "Merge Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
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
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = CardShape,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.card)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMergeMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
                Spacer(modifier = Modifier.width(Spacing.sm))
            }

            PatientAvatar(name = patient.name, isSelected = isSelected)

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patient.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                val clinicIdDisplay = if (patient.patientClinicId.isNullOrBlank() || patient.patientClinicId.startsWith("TEMP-")) "Not Assigned" else patient.patientClinicId
                PatientInfoSubtitle(
                    dob = patient.dob,
                    gender = patient.gender,
                    clinicId = clinicIdDisplay,
                    isSelected = isSelected
                )

                if (patient.phone.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = patient.phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (hasMissingPrice && !isMergeMode) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Vaccination price missing",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
            }

            if (!isMergeMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        shape = DialogShape,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (canEditOrDelete) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = { menuExpanded = false; onEdit() },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Merge") },
                            onClick = { menuExpanded = false; onLongClick() },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null) }
                        )
                        if (canEditOrDelete) {
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuExpanded = false; onDelete() },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientAvatar(name: String, isSelected: Boolean) {
    val initials = remember(name) {
        name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifEmpty { "?" }
    }
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun PatientInfoSubtitle(dob: String, gender: String, clinicId: String, isSelected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val ageLabel = remember(dob) { if (dob.isNotBlank()) calculateAgeLabel(dob) else null }
        val infoColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
        val items = mutableListOf<String>()
        if (ageLabel != null) items.add(ageLabel)
        if (gender.isNotBlank()) items.add(gender)
        items.add("ID: $clinicId")

        Text(
            text = items.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = infoColor
        )
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
        shape = DialogShape,
        title = { Text("Manual Merge", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Select the patient profile you want to KEEP. The other will be deleted and its vaccinations moved.")
                Spacer(modifier = Modifier.height(Spacing.lg))
                selectedPatients.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable { mergeMasterPatient = p }
                            .padding(Spacing.sm)
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
            snackbarHostState = remember { SnackbarHostState() },
            searchQuery = "",
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
            onRefresh = {}
        )
    }
}
