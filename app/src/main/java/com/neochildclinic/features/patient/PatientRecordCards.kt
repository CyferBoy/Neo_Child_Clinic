package com.neochildclinic.features.patient

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.utils.PatientUtils.formatDateForDisplay
import com.neochildclinic.domain.model.Consultation
import com.neochildclinic.data.local.entity.PatientNotesEntity
import io.github.jan.supabase.storage.FileObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Problem / Complaint | total fee
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = consultation.problem.ifBlank { "Consultation" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "₹${consultation.amount.toInt()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Row 2: Next Follow-up | payment breakdown
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (consultation.nextFollowUpDate.isNotBlank()) {
                    Text(
                        text = "Next: ${formatDateForDisplay(consultation.nextFollowUpDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                val paymentInfo = buildString {
                    if (consultation.cashAmount > 0) append("Cash: ₹${consultation.cashAmount.toInt()}")
                    if (consultation.cashAmount > 0 && consultation.onlineAmount > 0) append(" | ")
                    if (consultation.onlineAmount > 0) append("Online: ₹${consultation.onlineAmount.toInt()}")
                    val pending = consultation.amount - (consultation.cashAmount + consultation.onlineAmount)
                    if (pending > 0) {
                        if (isNotEmpty()) append(" | ")
                        append("Pending: ₹${pending.toInt()}")
                    }
                }
                if (paymentInfo.isNotEmpty()) {
                    Text(
                        text = paymentInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }

            // Row 3: Given date
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Given: ${formatDateForDisplay(consultation.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Row 4: Doctor name
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = displayDoctor,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
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
        DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm:ss a", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
            .format(date.toInstant())
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