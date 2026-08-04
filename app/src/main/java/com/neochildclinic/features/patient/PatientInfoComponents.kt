package com.neochildclinic.features.patient

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.data.local.entity.PatientNotesEntity
import com.neochildclinic.data.local.entity.AuditLogEntity
import com.neochildclinic.data.local.entity.InventoryDeductionEntity
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.Consultation
import io.github.jan.supabase.storage.FileObject
import com.neochildclinic.core.utils.PatientUtils.calculateAgeLabel
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay
import com.neochildclinic.features.reminder.FollowUpCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PatientDetailsContent(
    paddingValues: PaddingValues,
    patient: Patient,
    vaccinations: List<Vaccination>,
    consultations: List<Consultation>,
    documents: List<FileObject>,
    followUps: List<ReminderEntity>,
    notes: List<PatientNotesEntity>,
    canEditOrDelete: Boolean,
    selectedSegment: Int,
    onSegmentSelected: (Int) -> Unit,
    onEdit_vaccination: (String) -> Unit,
    onDeleteVaccination: (Vaccination) -> Unit,
    onUploadDocument: () -> Unit,
    onDeleteDocument: (String) -> Unit,
    onViewDocument: (String) -> Unit,
    viewModel: PatientViewModel
) {
    val scope = rememberCoroutineScope()
    var selectedVisitForDeductions by remember { mutableStateOf<String?>(null) }
    var deductionsForVisit by remember { mutableStateOf<List<InventoryDeductionEntity>>(emptyList()) }

    if (selectedVisitForDeductions != null) {
        InventoryDeductionsDialog(
            deductions = deductionsForVisit,
            onDismiss = { selectedVisitForDeductions = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp)
    ) {
        item { PatientInfoSection(patient) }

        item {
            HistorySegmentedButton(
                selectedSegment = selectedSegment,
                onSegmentSelected = onSegmentSelected
            )
        }

        when (selectedSegment) {
            0 -> {
                // Vaccination Segment
                if (vaccinations.isEmpty()) {
                    item { EmptySectionText("No vaccination records found.") }
                } else {
                    itemsIndexed(vaccinations, key = { _, v -> v.id }) { _, vaccination ->
                        VaccinationRecordCard(
                            vaccination = vaccination,
                            patient = patient,
                            canEditOrDelete = canEditOrDelete,
                            onEdit = { onEdit_vaccination(vaccination.id) },
                            onDelete = { onDeleteVaccination(vaccination) },
                            onShowInventoryIssues = { id ->
                                selectedVisitForDeductions = id
                                scope.launch {
                                    deductionsForVisit = viewModel.getInventoryDeductions(id)
                                }
                            }
                        )
                    }
                }
            }
            1 -> {
                // Consultation Segment
                if (consultations.isEmpty()) {
                    item { EmptySectionText("No consultation records found.") }
                } else {
                    items(consultations, key = { it.id }) { consultation ->
                        ConsultationRecordCard(consultation)
                    }
                }
            }
            2 -> {
                // Attachments Segment
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Documents", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onUploadDocument) {
                            Icon(Icons.Default.Upload, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Upload")
                        }
                    }
                }
                if (documents.isEmpty()) {
                    item { EmptySectionText("No attachments found.") }
                } else {
                    items(documents, key = { it.name }) { doc ->
                        DocumentCard(
                            doc = doc,
                            onView = { onViewDocument("${patient.id}/${doc.name}") },
                            onDelete = { onDeleteDocument("${patient.id}/${doc.name}") }
                        )
                    }
                }
            }
        }

        // Active Follow-ups Section
        val activeFollowUps = followUps.filter { it.status == "ACTIVE" || it.status == "RESCHEDULED" }
        if (activeFollowUps.isNotEmpty()) {
            item { SectionHeader("Active Follow-ups") }
            items(activeFollowUps, key = { it.id }) { followUp ->
                FollowUpCard(
                    reminder = followUp,
                    onActionClick = { /* Can add actions here later if needed */ }
                )
            }
        }

        // Clinical Notes Section
        if (notes.isNotEmpty()) {
            item { SectionHeader("Clinical Notes") }
            items(notes, key = { it.id }) { note ->
                ClinicalNoteCard(note)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySegmentedButton(
    selectedSegment: Int,
    onSegmentSelected: (Int) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        SegmentedButton(
            selected = selectedSegment == 0,
            onClick = { onSegmentSelected(0) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            icon = { SegmentedButtonDefaults.Icon(active = selectedSegment == 0) }
        ) {
            Text("Vax")
        }
        SegmentedButton(
            selected = selectedSegment == 1,
            onClick = { onSegmentSelected(1) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            icon = { SegmentedButtonDefaults.Icon(active = selectedSegment == 1) }
        ) {
            Text("Clinic")
        }
        SegmentedButton(
            selected = selectedSegment == 2,
            onClick = { onSegmentSelected(2) },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            icon = { SegmentedButtonDefaults.Icon(active = selectedSegment == 2) }
        ) {
            Text("Docs")
        }
    }
}

@Composable
fun DocumentCard(
    doc: FileObject,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("${(doc.metadata?.get("size")?.toString()?.toLongOrNull() ?: 0L) / 1024} KB", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onView) {
                Icon(Icons.Default.Visibility, "View")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
            }
        }
    }
}

@Composable
fun ConsultationRecordCard(consultation: Consultation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Consultation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "₹${consultation.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "DATE: ${formatDateForDisplay(consultation.date)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (consultation.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = consultation.notes, style = MaterialTheme.typography.bodyMedium)
            }
            if (consultation.nextFollowUpDate.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Next: ${formatDateForDisplay(consultation.nextFollowUpDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
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
fun ClinicalNoteCard(note: PatientNotesEntity) {
    val dateDisplay = remember(note.timestamp) { 
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(note.timestamp)) 
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "By: ${note.author}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(text = dateDisplay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = note.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text, 
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PatientInfoSection(patient: Patient) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = patient.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }

            val clinicIdDisplay = if (patient.patientClinicId.isBlank() || patient.patientClinicId.startsWith("TEMP-")) "Not Assigned" else patient.patientClinicId
            InfoRow(Icons.Default.Badge, "Clinic ID: $clinicIdDisplay")

            val ageLabel = calculateAgeLabel(patient.dob)
            InfoRow(Icons.Default.Cake, "${formatDateForDisplay(patient.dob)} (${ageLabel ?: "Unknown Age"})")
            
            InfoRow(if (patient.gender == "Male") Icons.Default.Male else Icons.Default.Female, patient.gender)

            if (patient.phone.isNotBlank()) {
                InfoRow(
                    icon = Icons.Default.Phone,
                    text = patient.phone,
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.phone}"))
                        context.startActivity(intent)
                    }
                )
            }

            if (patient.alternatePhone.isNotBlank()) {
                InfoRow(
                    icon = Icons.Default.ContactPhone,
                    text = "Alt: ${patient.alternatePhone}",
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${patient.alternatePhone}"))
                        context.startActivity(intent)
                    }
                )
            }

            if (patient.address.isNotBlank()) {
                InfoRow(Icons.Default.Home, patient.address)
            }
            
            Text(
                text = "Registered on: ${formatDateForDisplay(patient.registrationDate)}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun AuditLogDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    logs: List<AuditLogEntity>
) {
    if (!show) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audit Log", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                HorizontalDivider()
                
                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No audit logs found.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        items(logs) { log ->
                            AuditLogItem(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogItem(log: AuditLogEntity) {
    val date = remember(log.timestamp) { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(log.timestamp)) }
    val time = remember(log.timestamp) { SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(log.timestamp)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = log.action,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (log.entityType == "VACCINATION" || log.entityType == "VISIT") {
            Text(
                text = "ID: ${log.entityId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        val details = log.remarks ?: ""
        if (details.isNotBlank()) {
            Text(text = details, style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(text = "By:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = log.user, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        
        if (log.device != null) {
            Text(text = "Device: ${log.device}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Composable
fun InventoryDeductionsDialog(
    deductions: List<InventoryDeductionEntity>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inventory Deduction Status") },
        text = {
            if (deductions.isEmpty()) {
                Text("No detailed logs for this visit.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(deductions) { deduction ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(deduction.vaccineName, fontWeight = FontWeight.Bold)
                            Text(
                                text = deduction.status, 
                                color = if (deduction.status == "COMPLETED") Color(0xFF4CAF50) else Color.Red,
                                style = MaterialTheme.typography.labelSmall
                            )
                            if (deduction.errorMessage != null) {
                                Text(deduction.errorMessage, style = MaterialTheme.typography.bodySmall, color = Color.Red)
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp).alpha(0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PatientDetailsPreview() {
    // Cannot easily preview with ViewModel
}
