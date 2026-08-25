package com.neochildclinic.features.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.DarkBlueContainer
import com.neochildclinic.core.designsystem.DarkOnBlueContainer
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity

private enum class TodoTab { CONSULTATION, VACCINATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayPatientsCard(
    modifier: Modifier = Modifier,
    consultations: List<ConsultationTodoEntity>,
    vaccinations: List<VaccinationTodoEntity>,
    patients: List<Patient>,
    onAddConsultation: (Patient) -> Unit,
    onAddVaccination: (Patient, String) -> Unit,
    onDeleteConsultation: (String) -> Unit,
    onDeleteVaccination: (String) -> Unit,
    isDark: Boolean
) {
    var selectedTab by rememberSaveable { mutableStateOf(TodoTab.CONSULTATION) }
    var showAdd by remember { mutableStateOf(false) }

    val titleColor = if (isDark) DarkOnBlueContainer else androidx.compose.ui.graphics.Color(0xFF004977)
    val container = if (isDark) DarkBlueContainer else androidx.compose.ui.graphics.Color(0xFFE3F2FD)
    val list = if (selectedTab == TodoTab.CONSULTATION) consultations else vaccinations

    Card(
        modifier = modifier.height(190.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today's Patients", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = titleColor)
                        Text("Consultation: ${consultations.size}  •  Vaccination: ${vaccinations.size}", style = MaterialTheme.typography.labelSmall, color = titleColor.copy(alpha = .75f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == TodoTab.CONSULTATION,
                        onClick = { selectedTab = TodoTab.CONSULTATION },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Consultation") }
                    SegmentedButton(
                        selected = selectedTab == TodoTab.VACCINATION,
                        onClick = { selectedTab = TodoTab.VACCINATION },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Vaccination") }
                }
                Spacer(Modifier.height(6.dp))
                if (list.isEmpty()) {
                    Text(
                        "No patients added for today",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = titleColor.copy(alpha = .7f)
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(list.take(3), key = { if (selectedTab == TodoTab.CONSULTATION) (it as ConsultationTodoEntity).id else (it as VaccinationTodoEntity).id }) { item ->
                            if (selectedTab == TodoTab.CONSULTATION) {
                                val todo = item as ConsultationTodoEntity
                                TodoPreviewRow(todo.name, todo.mobile, onDelete = { onDeleteConsultation(todo.id) })
                            } else {
                                val todo = item as VaccinationTodoEntity
                                TodoPreviewRow(todo.name, todo.vaccineNames, onDelete = { onDeleteVaccination(todo.id) })
                            }
                        }
                    }
                }
            }

            FilledIconButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = titleColor, contentColor = MaterialTheme.colorScheme.surface)
            ) { Icon(Icons.Default.Add, contentDescription = if (selectedTab == TodoTab.CONSULTATION) "Add consultation patient" else "Add vaccination patient") }
        }
    }

    if (showAdd) {
        AddTodoDialog(
            tab = selectedTab,
            patients = patients,
            onDismiss = { showAdd = false },
            onAddConsultation = { patient -> onAddConsultation(patient); showAdd = false },
            onAddVaccination = { patient, vaccines -> onAddVaccination(patient, vaccines); showAdd = false }
        )
    }
}

@Composable
private fun TodoPreviewRow(name: String, detail: String, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove")
        }
    }
}

@Composable
private fun AddTodoDialog(
    tab: TodoTab,
    patients: List<Patient>,
    onDismiss: () -> Unit,
    onAddConsultation: (Patient) -> Unit,
    onAddVaccination: (Patient, String) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Patient?>(null) }
    var vaccines by rememberSaveable { mutableStateOf("") }
    val filtered = remember(search, patients) { 
        patients.filter { 
            it.name.contains(search, ignoreCase = true) || it.phone.contains(search) 
        }.take(25) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tab == TodoTab.CONSULTATION) "Add Consultation" else "Add Vaccination") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search, 
                    onValueChange = { search = it }, 
                    label = { Text("Search patient") }, 
                    singleLine = true, 
                    modifier = Modifier.fillMaxWidth()
                )
                if (selected == null) {
                    LazyColumn(Modifier.heightIn(max = 180.dp)) {
                        items(filtered, key = { it.id }) { patient ->
                            ListItem(
                                headlineContent = { Text(patient.name) },
                                supportingContent = { Text(patient.phone) },
                                modifier = Modifier.clickable { selected = patient }
                            )
                        }
                    }
                } else {
                    ListItem(
                        headlineContent = { Text(selected!!.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("${selected!!.phone}\n${selected!!.address.orEmpty()}") },
                        trailingContent = { TextButton(onClick = { selected = null }) { Text("Change") } }
                    )
                }
                if (tab == TodoTab.VACCINATION) {
                    OutlinedTextField(
                        value = vaccines, 
                        onValueChange = { vaccines = it }, 
                        label = { Text("Vaccine name(s)") }, 
                        placeholder = { Text("e.g. MMR, DPT") }, 
                        singleLine = false, 
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null && (tab == TodoTab.CONSULTATION || vaccines.isNotBlank()),
                onClick = { if (tab == TodoTab.CONSULTATION) onAddConsultation(selected!!) else onAddVaccination(selected!!, vaccines.trim()) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
