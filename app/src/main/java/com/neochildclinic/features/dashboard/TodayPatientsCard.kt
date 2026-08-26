package com.neochildclinic.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.data.local.entity.ConsultationTodoEntity
import com.neochildclinic.data.local.entity.VaccinationTodoEntity

private enum class TodoTab { CONSULTATION, VACCINATION }

@Composable
fun TodayPatientsCard(
    modifier: Modifier = Modifier,
    consultations: List<ConsultationTodoEntity>,
    vaccinations: List<VaccinationTodoEntity>,
    patients: List<Patient>,
    onTodayPatients: () -> Unit,
    onAddConsultation: (Patient) -> Unit,
    onAddVaccination: (Patient, String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(TodoTab.CONSULTATION) }
    var showAdd by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        DashboardCard(
            title = "Today's Patient",
            subtitle = "Consultation: ${consultations.size}\nVaccination: ${vaccinations.size}",
            icon = Icons.Default.EventAvailable,
            containerColor = SoftGreen,
            contentColor = TextGreen,
            modifier = Modifier.fillMaxSize(),
            onClick = onTodayPatients
        )
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
