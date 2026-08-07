package com.neochildclinic.features.patient

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
    doctorMap: Map<String, String>,
    canEditOrDelete: Boolean,
    selectedSegment: Int,
    onSegmentSelected: (Int) -> Unit,
    onLongClickVaccination: (Vaccination) -> Unit,
    onLongClickConsultation: (Consultation) -> Unit,
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
        contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp)
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
                if (vaccinations.isEmpty()) {
                    item { EmptySectionText("No vaccination records found.") }
                } else {
                    itemsIndexed(vaccinations, key = { _, v -> v.id }) { _, vaccination ->
                        VaccinationRecordCard(
                            vaccination = vaccination,
                            patient = patient,
                            doctorName = doctorMap[vaccination.doctorId] ?: vaccination.performedBy,
                            onLongClick = { onLongClickVaccination(vaccination) },
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
                if (consultations.isEmpty()) {
                    item { EmptySectionText("No consultation records found.") }
                } else {
                    items(consultations, key = { it.id }) { consultation ->
                        ConsultationRecordCard(
                            consultation = consultation,
                            doctorName = doctorMap[consultation.doctorId] ?: consultation.doctorName,
                            onLongClick = { onLongClickConsultation(consultation) }
                        )
                    }
                }
            }
        }

        // Keep Attachments and Notes visible at the bottom or as separate sections?
        // Spec implies scrollable screen. I'll keep them as sections if they exist.
        
        val activeFollowUps = followUps.filter { it.status == "ACTIVE" || it.status == "RESCHEDULED" }
        if (activeFollowUps.isNotEmpty()) {
            item { SectionHeader("Active Follow-ups") }
            items(activeFollowUps, key = { it.id }) { followUp ->
                FollowUpCard(reminder = followUp, onActionClick = { })
            }
        }

        if (notes.isNotEmpty()) {
            item { SectionHeader("Clinical Notes") }
            items(notes, key = { it.id }) { note ->
                ClinicalNoteCard(note)
            }
        }

        if (documents.isNotEmpty()) {
            item { 
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Documents")
                    TextButton(onClick = onUploadDocument) {
                        Icon(Icons.Default.Upload, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Upload")
                    }
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySegmentedButton(
    selectedSegment: Int,
    onSegmentSelected: (Int) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selectedSegment == 0,
            onClick = { onSegmentSelected(0) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { SegmentedButtonDefaults.Icon(active = selectedSegment == 0) }
        ) { Text("Vaccination") }
        SegmentedButton(
            selected = selectedSegment == 1,
            onClick = { onSegmentSelected(1) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { SegmentedButtonDefaults.Icon(active = selectedSegment == 1) }
        ) { Text("Consultation") }
    }
}

@Composable
fun PatientInfoSection(patient: Patient) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = patient.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = patient.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val clinicId = if (patient.patientClinicId?.startsWith("TEMP-") == true) "Not Assigned" else patient.patientClinicId ?: "Not Assigned"
                    Text(text = "ID: $clinicId", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(modifier = Modifier.alpha(0.3f))

            val ageLabel = calculateAgeLabel(patient.dob)
            InfoGridRow(
                Pair(Icons.Default.Cake, "${formatDateForDisplay(patient.dob)} ($ageLabel)"),
                Pair(if (patient.gender == "Male") Icons.Default.Male else Icons.Default.Female, patient.gender)
            )

            InfoGridRow(
                Pair(Icons.Default.Phone, patient.phone),
                Pair(Icons.Default.CalendarToday, "Reg: ${formatDateForDisplay(patient.registrationDate ?: "")}")
            )

            if (patient.address?.isNotBlank() == true) {
                InfoRow(Icons.Default.Home, patient.address ?: "")
            }
        }
    }
}

@Composable
private fun InfoGridRow(left: Pair<androidx.compose.ui.graphics.vector.ImageVector, String>, right: Pair<androidx.compose.ui.graphics.vector.ImageVector, String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f)) {
            InfoRow(left.first, left.second)
        }
        Box(modifier = Modifier.weight(1f)) {
            InfoRow(right.first, right.second)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConsultationRecordCard(
    consultation: Consultation,
    doctorName: String = "",
    onLongClick: () -> Unit = {}
) {
    val displayDoctor = doctorName.ifBlank { consultation.doctorName }.ifBlank { "Unknown Doctor" }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Consultation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (consultation.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = consultation.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = displayDoctor,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatDateForDisplay(consultation.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${consultation.amount}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                val paymentBreakdown = buildString {
                    if (consultation.cashAmount > 0) append("Cash: ₹${consultation.cashAmount.toInt()}")
                    if (consultation.cashAmount > 0 && consultation.onlineAmount > 0) append("\n")
                    if (consultation.onlineAmount > 0) append("Online: ₹${consultation.onlineAmount.toInt()}")
                    val pending = consultation.amount - (consultation.cashAmount + consultation.onlineAmount)
                    if (pending > 0) {
                        if (isNotEmpty()) append("\n")
                        append("Pending: ₹${pending.toInt()}")
                    }
                }
                
                if (paymentBreakdown.isNotEmpty()) {
                    Text(
                        text = paymentBreakdown,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun DocumentCard(doc: FileObject, onView: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("${(doc.metadata?.get("size")?.toString()?.toLongOrNull() ?: 0L) / 1024} KB", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onView) { Icon(Icons.Default.Visibility, null) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
        }
    }
}

@Composable
fun ClinicalNoteCard(note: PatientNotesEntity) {
    val dateDisplay = remember(note.timestamp) { 
        val date = com.neochildclinic.core.utils.PatientUtils.parseDate(note.timestamp) ?: Date(0)
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(date)
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
private fun SectionHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun EmptySectionText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun InventoryDeductionsDialog(deductions: List<InventoryDeductionEntity>, onDismiss: () -> Unit) {
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
                            Text(text = deduction.status, color = if (deduction.status == "COMPLETED") Color(0xFF4CAF50) else Color.Red, style = MaterialTheme.typography.labelSmall)
                            if (deduction.errorMessage != null) {
                                Text(deduction.errorMessage, style = MaterialTheme.typography.bodySmall, color = Color.Red)
                            }
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp).alpha(0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
