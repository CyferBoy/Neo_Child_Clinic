package com.neochildclinic.features.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.*

@Composable
private fun DashboardPatientCard(
    modifier: Modifier,
    count: Int,
    onPatientList: () -> Unit,
    onAddPatient: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val container = if (isDark) DarkBlueContainer else Color(0xFFE3F2FD)
    val content = if (isDark) DarkOnBlueContainer else Color(0xFF004977)
    Card(
        onClick = onPatientList,
        modifier = modifier.height(190.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(40.dp), tint = content)
                Spacer(Modifier.height(10.dp))
                Text("Patients", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = content)
                Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = content)
            }
            FilledIconButton(
                onClick = onAddPatient,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = content, contentColor = MaterialTheme.colorScheme.surface)
            ) { Icon(Icons.Default.Add, contentDescription = "Add patient") }
        }
    }
}

@Composable
fun DashboardMainGrid(
    isWideScreen: Boolean,
    uiState: DashboardUiState,
    onPatientList: () -> Unit,
    onAddPatient: () -> Unit,
    dashboardAddConsultation: (com.neochildclinic.domain.model.Patient) -> Unit,
    dashboardAddVaccination: (com.neochildclinic.domain.model.Patient, String) -> Unit,
    dashboardDeleteConsultation: (String) -> Unit,
    dashboardDeleteVaccination: (String) -> Unit,
    onInventory: () -> Unit,
    onStatistics: () -> Unit
) {
    if (isWideScreen) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardPatientCard(
                    modifier = Modifier.weight(1f), count = uiState.patientCount,
                    onPatientList = onPatientList, onAddPatient = onAddPatient
                )
                TodayPatientsCard(
                    modifier = Modifier.weight(1f),
                    consultations = uiState.todayConsultations,
                    vaccinations = uiState.todayVaccinations,
                    patients = uiState.patients,
                    onAddConsultation = dashboardAddConsultation,
                    onAddVaccination = dashboardAddVaccination,
                    onDeleteConsultation = dashboardDeleteConsultation,
                    onDeleteVaccination = dashboardDeleteVaccination,
                    isDark = isSystemInDarkTheme()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = "Inventory",
                    icon = Icons.Default.ShoppingCart,
                    containerColor = if (isSystemInDarkTheme()) DarkOrangeContainer else Color(0xFFFFF3E0),
                    contentColor = if (isSystemInDarkTheme()) DarkOnOrangeContainer else Color(0xFFE65100),
                    badge = if (uiState.lowStockCount > 0) "${uiState.lowStockCount} Low" else null,
                    height = 160.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onInventory
                )
                DashboardCard(
                    title = "Statistics",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    containerColor = if (isSystemInDarkTheme()) DarkPurpleContainer else Color(0xFFF3E5F5),
                    contentColor = if (isSystemInDarkTheme()) DarkOnPurpleContainer else Color(0xFF4A148C),
                    height = 160.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onStatistics
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardPatientCard(
                    modifier = Modifier.fillMaxWidth(), count = uiState.patientCount,
                    onPatientList = onPatientList, onAddPatient = onAddPatient
                )
                DashboardCard(
                    title = "Statistics",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    containerColor = if (isSystemInDarkTheme()) DarkPurpleContainer else Color(0xFFF3E5F5),
                    contentColor = if (isSystemInDarkTheme()) DarkOnPurpleContainer else Color(0xFF4A148C),
                    height = 140.dp,
                    onClick = onStatistics
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TodayPatientsCard(
                    modifier = Modifier.fillMaxWidth(),
                    consultations = uiState.todayConsultations,
                    vaccinations = uiState.todayVaccinations,
                    patients = uiState.patients,
                    onAddConsultation = dashboardAddConsultation,
                    onAddVaccination = dashboardAddVaccination,
                    onDeleteConsultation = dashboardDeleteConsultation,
                    onDeleteVaccination = dashboardDeleteVaccination,
                    isDark = isSystemInDarkTheme()
                )
                DashboardCard(
                    title = "Inventory",
                    icon = Icons.Default.ShoppingCart,
                    containerColor = if (isSystemInDarkTheme()) DarkOrangeContainer else Color(0xFFFFF3E0),
                    contentColor = if (isSystemInDarkTheme()) DarkOnOrangeContainer else Color(0xFFE65100),
                    badge = if (uiState.lowStockCount > 0) "${uiState.lowStockCount} Low" else null,
                    height = 200.dp,
                    onClick = onInventory
                )
            }
        }
    }
}

@Composable
fun DashboardSmallActionsRow(
    uiState: DashboardUiState,
    onBorrowed: () -> Unit,
    onDue: () -> Unit,
    onWaste: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DashboardCardSmall(
            title = "Borrowed",
            icon = Icons.Default.SwapHoriz,
            containerColor = if (isSystemInDarkTheme()) Color(0xFF004D40) else Color(0xFFE0F2F1),
            contentColor = if (isSystemInDarkTheme()) Color(0xFF80CBC4) else Color(0xFF00695C),
            badge = if (uiState.borrowedCount > 0) uiState.borrowedCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onBorrowed
        )
        @Suppress("DEPRECATION")
        DashboardCardSmall(
            title = "Due",
            icon = Icons.Default.EventNote,
            containerColor = if (isSystemInDarkTheme()) DarkBrownContainer else Color(0xFFEFEBE9),
            contentColor = if (isSystemInDarkTheme()) DarkOnBrownContainer else Color(0xFF3E2723),
            badge = if (uiState.dueTodayCount > 0) uiState.dueTodayCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onDue
        )
        DashboardCardSmall(
            title = "Waste",
            icon = Icons.Default.DeleteSweep,
            containerColor = if (isSystemInDarkTheme()) DarkRedContainer else Color(0xFFFBE9E7),
            contentColor = if (isSystemInDarkTheme()) DarkOnRedContainer else Color(0xFFBF360C),
            badge = if (uiState.wasteCount > 0) uiState.wasteCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onWaste
        )
    }
}
