package com.neochildclinic.features.statistics

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.features.patient.PatientViewModel
import com.neochildclinic.core.utils.PatientUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyFinanceDetailsScreen(
    monthKey: String,
    onBack: () -> Unit,
    viewModel: PatientViewModel = hiltViewModel()
) {
    val allPatients by viewModel.allPatients.collectAsState()
    val allVaccinations by viewModel.allVaccinations.collectAsState()

    val title = remember(monthKey) {
        val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val monthIdx = monthKey.substringAfter("-").toInt()
        val year = monthKey.substringBefore("-")
        "${monthNames[monthIdx]} $year"
    }

    val filteredVaccinations = remember(allVaccinations, monthKey) {
        allVaccinations.filter { v ->
            val cal = Calendar.getInstance().apply { 
                time = PatientUtils.parseDate(v.dateGiven) ?: Date(0)
            }
            val key = String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
            key == monthKey
        }.sortedByDescending { PatientUtils.parseDate(it.dateGiven) }
    }

    val totals = remember(filteredVaccinations) {
        val cash = filteredVaccinations.sumOf { it.cashAmount }
        val online = filteredVaccinations.sumOf { it.onlineAmount }
        cash to online
    }

    MonthlyFinanceDetailsContent(
        title = title,
        onBack = onBack,
        isLoading = allVaccinations.isEmpty() || allPatients.isEmpty(),
        vaccinations = filteredVaccinations,
        patients = allPatients,
        totalCash = totals.first,
        totalOnline = totals.second
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthlyFinanceDetailsContent(
    title: String,
    onBack: () -> Unit,
    isLoading: Boolean,
    vaccinations: List<Vaccination>,
    patients: List<Patient>,
    totalCash: Double,
    totalOnline: Double
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            if (isLoading && vaccinations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (vaccinations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text("No records found for this month")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { FinanceSummaryCards(totalCash, totalOnline) }

                    items(vaccinations, key = { it.id }) { v ->
                        val patient = remember(v.patientId, patients) { patients.find { it.id == v.patientId } }
                        FinanceRecordCard(v, patient)
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceSummaryCards(cash: Double, online: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(
            label = "Cash",
            amount = cash,
            containerColor = if (isSystemInDarkTheme()) Color(0xFF1B372D) else Color(0xFFE8F5E9),
            contentColor = if (isSystemInDarkTheme()) Color(0xFF8FF7BF) else Color(0xFF2E7D32),
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            label = "Online",
            amount = online,
            containerColor = if (isSystemInDarkTheme()) Color(0xFF003355) else Color(0xFFE3F2FD),
            contentColor = if (isSystemInDarkTheme()) Color(0xFFC2E8FF) else Color(0xFF1565C0),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(label: String, amount: Double, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("₹${amount.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FinanceRecordCard(vaccination: Vaccination, patient: Patient?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = patient?.name ?: "Unknown Patient", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(text = "₹${vaccination.totalPaid.toInt()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = vaccination.items.joinToString(", ") { PatientUtils.cleanVaccineName(it.vaccineName) }, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Date: ${PatientUtils.formatDateForDisplay(vaccination.dateGiven)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val mode = when {
                    vaccination.cashAmount > 0 && vaccination.onlineAmount > 0 -> "Cash + Online"
                    vaccination.cashAmount > 0 -> "Cash"
                    vaccination.onlineAmount > 0 -> "Online"
                    else -> "N/A"
                }
                Text(text = "Mode: $mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (vaccination.performedBy.isNotBlank()) {
                Text(
                    text = "Added by: ${vaccination.performedBy}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonthlyFinancePreview() {
    NeoChildTheme {
        MonthlyFinanceDetailsContent(
            title = "January 2024",
            onBack = {},
            isLoading = false,
            vaccinations = emptyList(),
            patients = emptyList(),
            totalCash = 5000.0,
            totalOnline = 3000.0
        )
    }
}
