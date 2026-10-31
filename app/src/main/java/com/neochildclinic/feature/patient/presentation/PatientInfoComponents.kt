package com.neochildclinic.feature.patient.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neochildclinic.domain.model.InventoryDeduction
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.domain.model.PatientDocument
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.common.PatientUtils.formatDateForDisplay
import com.neochildclinic.core.common.PatientUtils.formatAgeYearsMonths
import com.neochildclinic.core.designsystem.*
import kotlinx.coroutines.launch

@Composable
fun PatientDetailsContent(
    paddingValues: PaddingValues,
    patient: Patient,
    history: PatientHistoryUiState,
    documents: List<PatientDocument>,
    doctorMap: Map<String, String>,
    vaccineMap: Map<String, String>,
    canEditOrDelete: Boolean,
    selectedSegment: Int,
    onSegmentSelected: (Int) -> Unit,
    onLongClickVaccination: (Vaccination) -> Unit,
    onLongClickConsultation: (Consultation) -> Unit,
    onOpenVaccinationDetails: (Vaccination) -> Unit = {},
    onUploadDocument: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onViewDocument: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    viewModel: PatientViewModel,
    onEditPatient: () -> Unit = {},
    onShowAudit: () -> Unit = {},
    onReceiptAction: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cardsByVaccinationId = remember(history.vaccinationCards) {
        history.vaccinationCards.associateBy { it.vaccination.id }
    }
    var selectedVisitForDeductions by remember { mutableStateOf<String?>(null) }
    var deductionsForVisit by remember { mutableStateOf<List<InventoryDeduction>>(emptyList()) }

    if (selectedVisitForDeductions != null) {
        InventoryDeductionsDialog(
            deductions = deductionsForVisit,
            onDismiss = { selectedVisitForDeductions = null }
        )
    }

    AppPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(bottom = 100.dp, top = Spacing.md)
        ) {
            item {
                PatientInfoSection(
                    patient = patient,
                    onCall = {
                        val phoneNumber = patient.phone.trim()
                        if (phoneNumber.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}")))
                        }
                    },
                    onEdit = onEditPatient,
                    onAudit = onShowAudit,
                    onReceipt = onReceiptAction
                )
            }

            item {
                HistorySegmentedButton(
                    selectedSegment = selectedSegment,
                    onSegmentSelected = onSegmentSelected
                )
            }

            when (selectedSegment) {
                0 -> {
                    if (!history.isLoaded) {
                        item { SkeletonList(count = 3) }
                    } else {
                        val emptyMessage = history.segmentEmptyMessage(PatientHistoryUiState.SEGMENT_VACCINATIONS)
                        if (emptyMessage != null) {
                            item { EmptySectionText(emptyMessage) }
                        } else {
                            itemsIndexed(history.vaccinations, key = { _, v -> v.id }) { _, vaccination ->
                                val cardData = cardsByVaccinationId[vaccination.id]
                                VaccinationRecordCard(
                                    vaccination = vaccination,
                                    patient = patient,
                                    doctorName = doctorMap[vaccination.doctorId] ?: vaccination.performedBy,
                                    reminders = cardData?.reminders.orEmpty(),
                                    vaccineMap = vaccineMap,
                                    onClick = { onOpenVaccinationDetails(vaccination) },
                                    onLongClick = { onLongClickVaccination(vaccination) },
                                    onShowInventoryIssues = { id ->
                                        selectedVisitForDeductions = id
                                        scope.launch {
                                            deductionsForVisit = viewModel.getInventoryDeductions(id)
                                        }
                                    }
                                )
                            }

                            // Footer note matching patient_details_vaccination_light.svg (y=766)
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Tap a record for doses, batch and inventory deductions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (!history.isLoaded) {
                        item { SkeletonList(count = 3) }
                    } else {
                        val emptyMessage = history.segmentEmptyMessage(PatientHistoryUiState.SEGMENT_CONSULTATIONS)
                        if (emptyMessage != null) {
                            item { EmptySectionText(emptyMessage) }
                        } else {
                            items(history.consultations, key = { it.id }) { consultation ->
                                ConsultationRecordCard(
                                    consultation = consultation,
                                    doctorName = doctorMap[consultation.doctorId] ?: consultation.doctorName,
                                    onLongClick = { onLongClickConsultation(consultation) }
                                )
                            }

                            // Footer note matching patient_details_vaccination_light.svg (y=766)
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Spacing.md),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Tap a record for doses, batch and inventory deductions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (history.isLoaded && history.notes.isNotEmpty()) {
                item { SectionHeader("Clinical Notes") }
                items(history.notes, key = { it.id }) { note ->
                    ClinicalNoteCard(note)
                }
            }

            if (documents.isNotEmpty()) {
                item { 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader("Documents")
                        TextButton(onClick = onUploadDocument) {
                            Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Upload", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                items(documents, key = { it.path }) { doc ->
                    DocumentCard(
                        doc = doc,
                        onView = { onViewDocument(doc.path) },
                        onDelete = { onDeleteDocument(doc.path) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistorySegmentedButton(
    selectedSegment: Int,
    onSegmentSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSegmentSelected(0) },
                shape = RoundedCornerShape(10.dp),
                color = if (selectedSegment == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                border = if (selectedSegment == 0) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                shadowElevation = if (selectedSegment == 0) 1.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Vaccination",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedSegment == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedSegment == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSegmentSelected(1) },
                shape = RoundedCornerShape(10.dp),
                color = if (selectedSegment == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                border = if (selectedSegment == 1) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                shadowElevation = if (selectedSegment == 1) 1.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Consultation",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedSegment == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedSegment == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PatientInfoSection(
    patient: Patient,
    onCall: () -> Unit = {},
    onEdit: () -> Unit = {},
    onAudit: () -> Unit = {},
    onReceipt: () -> Unit = {}
) {
    val initials = remember(patient.name) {
        patient.name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifBlank { "P" }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = patient.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val clinicId = if (patient.patientClinicId?.startsWith("TEMP-") == true) "Not Assigned" else patient.patientClinicId ?: "Not Assigned"
                    val ageLabel = formatAgeYearsMonths(patient.dob)
                    val subTitle = listOfNotNull(
                        ageLabel,
                        patient.gender.takeIf { it.isNotBlank() },
                        clinicId
                    ).joinToString(" · ")
                    Text(
                        text = subTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Active patient",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Quick Actions Row (Call, Edit, Audit, Receipt)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PatientActionPill(
                    icon = Icons.Default.Call,
                    label = "Call",
                    modifier = Modifier.weight(1f),
                    onClick = onCall
                )
                PatientActionPill(
                    icon = Icons.Default.Edit,
                    label = "Edit",
                    modifier = Modifier.weight(1f),
                    onClick = onEdit
                )
                PatientActionPill(
                    icon = Icons.Default.History,
                    label = "Audit",
                    modifier = Modifier.weight(1f),
                    onClick = onAudit
                )
                PatientActionPill(
                    icon = Icons.Default.Receipt,
                    label = "Receipt",
                    modifier = Modifier.weight(1f),
                    onClick = onReceipt
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )

            val dobDisplay = formatDateForDisplay(patient.dob)

            InfoGridRow(
                Pair(Icons.Default.Cake, "DOB: $dobDisplay"),
                Pair(if (patient.gender == "Male") Icons.Default.Male else Icons.Default.Female, patient.gender)
            )

            InfoGridRow(
                Pair(Icons.Default.Phone, patient.phone.ifBlank { "No phone" }),
                Pair(Icons.Default.CalendarToday, "Reg: " + formatDateForDisplay(patient.registrationDate ?: "")),
                leftClickable = patient.phone.isNotBlank(),
                onLeftClick = onCall
            )

            if (patient.address?.isNotBlank() == true) {
                InfoRow(Icons.Default.Home, patient.address ?: "")
            }
        }
    }
}

@Composable
private fun PatientActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoGridRow(
    left: Pair<androidx.compose.ui.graphics.vector.ImageVector, String>,
    right: Pair<androidx.compose.ui.graphics.vector.ImageVector, String>,
    leftClickable: Boolean = false,
    onLeftClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xl)
    ) {
        Box(
            modifier = if (leftClickable) {
                Modifier.weight(1f).clickable(onClick = onLeftClick)
            } else {
                Modifier.weight(1f)
            },
            contentAlignment = Alignment.CenterStart
        ) {
            InfoRow(left.first, left.second)
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            InfoRow(right.first, right.second)
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}

@Composable
private fun EmptySectionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun InventoryDeductionsDialog(deductions: List<InventoryDeduction>, onDismiss: () -> Unit) {
    val customColors = LocalCustomColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Inventory Deduction Status", fontWeight = FontWeight.Bold) },
        text = {
            if (deductions.isEmpty()) {
                Text("No detailed logs for this visit.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(deductions) { deduction ->
                        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                            Text(deduction.vaccineName, fontWeight = FontWeight.Bold)
                            Text(
                                text = deduction.status,
                                color = if (deduction.status == "COMPLETED") customColors.textGreen
                                        else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (deduction.errorMessage != null) {
                                Text(
                                    deduction.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(top = Spacing.xs),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
