package com.clinic.neochild.features.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clinic.neochild.core.ui.AppBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddPatient: () -> Unit = {},
    onPatientList: () -> Unit = {},
    onAddVaccine: () -> Unit = {},
    onStatistics: () -> Unit = {},
    onBorrowed: () -> Unit = {},
    onDue: () -> Unit = {},
    onWaste: () -> Unit = {},
    onManageStaff: () -> Unit = {},
    onLogout: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSync: () -> Unit = {},
    onAuditLogs: () -> Unit = {},
    onProfile: () -> Unit = {},
    onSearch: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val role = uiState.staff?.role ?: "Staff"
    val showManageStaff = role == "Admin" || role == "Doctor"
    val showAuditLogs = role == "Admin"
    val showStatistics = role == "Admin" || role == "Doctor"

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    AppBackground { 
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DashboardTopBar(
                    staff = uiState.staff,
                    showManageStaff = showManageStaff,
                    showAuditLogs = showAuditLogs,
                    onManageStaff = onManageStaff,
                    onSettings = onSettings,
                    onSync = onSync,
                    onAuditLogs = onAuditLogs,
                    onProfile = onProfile,
                    onSearch = onSearch,
                    onLogout = {
                        authViewModel.logout()
                        onLogout()
                    }
                )
            }
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp)
            ) {
                val isWideScreen = maxWidth > 600.dp
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ClinicLogo(isWideScreen)
                    
                    Text(
                        text = "Dashboard",
                        style = if (isWideScreen) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    DashboardMainGrid(
                        isWideScreen = isWideScreen,
                        uiState = uiState,
                        showStatistics = showStatistics,
                        onPatientList = onPatientList,
                        onAddPatient = onAddPatient,
                        onInventory = onAddVaccine,
                        onStatistics = onStatistics
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    DashboardSmallActionsRow(
                        uiState = uiState,
                        onBorrowed = onBorrowed,
                        onDue = onDue,
                        onWaste = onWaste
                    )
                    
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}
