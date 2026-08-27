package com.neochildclinic.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neochildclinic.core.designsystem.LocalCustomColors
import com.neochildclinic.core.designsystem.*

@Composable
private fun DashboardPatientCard(
    modifier: Modifier,
    count: Int,
    onPatientList: () -> Unit,
    onAddPatient: () -> Unit
) {
    val customColors = LocalCustomColors.current
    Box(
        modifier = modifier
    ) {
        DashboardCard(
            title = "Patient List",
            subtitle = "Total Patient: $count",
            icon = Icons.AutoMirrored.Filled.List,
            containerColor = customColors.softBlue,
            contentColor = customColors.textBlue,
            modifier = Modifier.fillMaxSize(),
            onClick = onPatientList
        )
        // Bottom-right docked '+' action button
        Box(
            modifier = Modifier
                .size(55.dp)
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(topStart = 16.dp, bottomEnd = 24.dp))
                .background(Color(0xFF03A9F4))
                .clickable { onAddPatient() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Patient",
                tint = Color.White
            )
        }
    }
}

@Composable
fun DashboardMainGrid(
    isWideScreen: Boolean,
    uiState: DashboardUiState,
    onPatientList: () -> Unit,
    onAddPatient: () -> Unit,
    onTodayPatients: () -> Unit,
    dashboardAddConsultation: (com.neochildclinic.domain.model.Patient) -> Unit,
    dashboardAddVaccination: (com.neochildclinic.domain.model.Patient, String) -> Unit,
    onInventory: () -> Unit,
    onStatistics: () -> Unit
) {
    val customColors = LocalCustomColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Column 1: Patient List (Larger) and Statistics (Smaller)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DashboardPatientCard(
                modifier = Modifier.fillMaxWidth().height(210.dp),
                count = uiState.patientCount,
                onPatientList = onPatientList,
                onAddPatient = onAddPatient
            )
            DashboardCard(
                title = "Statistics",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                containerColor = customColors.softPurple,
                contentColor = customColors.textPurple,
                height = 150.dp, // Slightly smaller height
                modifier = Modifier.fillMaxWidth(),
                onClick = onStatistics
            )
        }

        // Column 2: Today's Patient (Smaller) and Inventory (Larger)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TodayPatientsCard(
                modifier = Modifier.fillMaxWidth().height(150.dp), // Slightly smaller height
                consultations = uiState.todayConsultations,
                vaccinations = uiState.todayVaccinations,
                patients = uiState.patients,
                onTodayPatients = onTodayPatients,
                onAddConsultation = dashboardAddConsultation,
                onAddVaccination = dashboardAddVaccination
            )
            DashboardCard(
                title = "Inventory",
                icon = Icons.Default.ShoppingCart,
                containerColor = customColors.softOrange,
                contentColor = if (uiState.lowStockCount > 0) customColors.textPink else customColors.textOrange,
                badge = if (uiState.lowStockCount > 0) "${uiState.lowStockCount} Low" else null,
                height = 210.dp, // Maintain larger height
                modifier = Modifier.fillMaxWidth(),
                onClick = onInventory
            )
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
    val customColors = LocalCustomColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DashboardCardSmall(
            title = "Borrowed",
            icon = Icons.Default.SwapHoriz,
            containerColor = customColors.softCyan,
            contentColor = customColors.textCyan,
            badge = if (uiState.borrowedCount > 0) uiState.borrowedCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onBorrowed
        )
        DashboardCardSmall(
            title = "Due",
            icon = Icons.Default.CalendarToday,
            containerColor = customColors.softGrey,
            contentColor = customColors.textGrey,
            badge = if (uiState.dueTodayCount > 0) uiState.dueTodayCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onDue
        )
        DashboardCardSmall(
            title = "Waste",
            icon = Icons.Default.Delete,
            containerColor = customColors.softPink,
            contentColor = customColors.textPink,
            badge = if (uiState.wasteCount > 0) uiState.wasteCount.toString() else null,
            modifier = Modifier.weight(1f),
            onClick = onWaste
        )
    }
}
