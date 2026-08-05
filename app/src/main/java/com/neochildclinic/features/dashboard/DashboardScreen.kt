package com.neochildclinic.features.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.UserRole
import com.neochildclinic.domain.repository.SyncState
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.features.dashboard.components.AppDrawer
import com.neochildclinic.app.Routes
import kotlinx.coroutines.launch

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
    val authProfile by authViewModel.profile.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val role = authProfile?.role ?: UserRole.nurse
    val showStatistics = role == UserRole.admin || role == UserRole.doctor

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.syncState, uiState.errorMessage) {
        if (uiState.syncState == SyncState.ERROR) {
            snackbarHostState.showSnackbar("Working in offline mode.")
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                userName = authProfile?.displayName ?: "User",
                userRole = role,
                syncState = uiState.syncState,
                appVersion = "1.0.0",
                onProfileClick = {
                    scope.launch { drawerState.close() }
                    onProfile()
                },
                onNavigate = { route: String ->
                    scope.launch { drawerState.close() }
                    when (route) {
                        "dashboard" -> {} // Already here
                        "patient_list" -> onPatientList()
                        "due" -> onDue()
                        "vaccine_inventory" -> onAddVaccine()
                        "statistics" -> onStatistics()
                        "manage_staff" -> onManageStaff()
                        "audit_logs" -> onAuditLogs()
                    }
                },
                onLogout = {
                    scope.launch { drawerState.close() }
                    authViewModel.logout()
                    onLogout()
                },
                onSyncClick = {
                    onSync()
                    dashboardViewModel.refresh()
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    onSettings()
                },
                currentRoute = "dashboard"
            )
        }
    ) {
        AppBackground { 
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    DashboardTopBar(
                        onMenuClick = { scope.launch { drawerState.open() } }
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
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ClinicLogo(isWideScreen)
                        
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
}
