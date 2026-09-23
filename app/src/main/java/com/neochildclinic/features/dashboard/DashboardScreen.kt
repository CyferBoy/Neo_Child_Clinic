package com.neochildclinic.features.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.neochildclinic.data.repository.SyncState
import com.neochildclinic.core.ui.SkeletonBox
import com.neochildclinic.core.ui.SkeletonCard
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.features.dashboard.components.AppDrawer
import com.neochildclinic.app.Routes
import com.neochildclinic.core.designsystem.LocalCustomColors
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
    onTodayPatients: () -> Unit = {},
    onPersonalReminders: () -> Unit = {},
    onExpenses: () -> Unit = {},
    onDoctorTimings: () -> Unit = {},
    onManageStaff: () -> Unit = {},
    onLogout: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSync: () -> Unit = {},
    onAuditLogs: () -> Unit = {},
    onProfile: () -> Unit = {},
    onSearch: () -> Unit = {},
    authViewModel: AuthViewModel,
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by dashboardViewModel.uiState.collectAsState()
    val authProfile by authViewModel.profile.collectAsState()
    val isProfileLoading by authViewModel.isProfileLoading.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Don't render the role-gated dashboard/drawer until the real profile has resolved.
    // Defaulting to UserRole.nurse while authProfile is still null (the old behavior)
    // is what made admin/doctor accounts intermittently flash the nurse view on cold
    // start or a fast reopen.
    if (isProfileLoading && authProfile == null) {
        // Skeleton shown while the profile (and therefore the real dashboard content) is
        // still resolving - see the comment above for why this gate exists at all. It renders
        // inside the same shell (background + top bar) and mirrors the real dashboard layout:
        // logo header, the 2-column tile grid (210/150 and 150/210 heights, 16dp gaps), then
        // the 3-tile Borrowed/Due/Waste row - so the loading state reads as the dashboard.
        val customColors = LocalCustomColors.current
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = customColors.bgOffWhite
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { DashboardTopBar(onMenuClick = {}) }
            ) { paddingValues ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                ) {
                    val isWideScreen = maxWidth > 600.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // ClinicLogo placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SkeletonBox(
                                modifier = Modifier.size(if (isWideScreen) 180.dp else 140.dp),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // DashboardMainGrid placeholder: same 2 columns, same tile heights
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 210.dp,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 150.dp,
                                    shape = RoundedCornerShape(24.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 150.dp,
                                    shape = RoundedCornerShape(24.dp)
                                )
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 210.dp,
                                    shape = RoundedCornerShape(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // DashboardSmallActionsRow placeholder (Borrowed / Due / Waste)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            repeat(3) {
                                SkeletonCard(
                                    modifier = Modifier.weight(1f),
                                    height = 90.dp,
                                    shape = RoundedCornerShape(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
        return
    }

    val role = authProfile?.role ?: UserRole.nurse

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
                isOnline = uiState.isOnline,
                pendingSyncCount = uiState.pendingSyncCount,
                appVersion = com.neochildclinic.BuildConfig.VERSION_NAME,
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
                        "personal_reminders" -> onPersonalReminders()
                        "expenses" -> onExpenses()
                        "doctor_timings" -> onDoctorTimings()
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
                    // Close the drawer before navigating to Cloud Synchronization.
                    // This ensures that returning from SyncScreen reveals the Dashboard
                    // normally instead of reopening the drawer over it.
                    scope.launch { drawerState.close() }
                    if (uiState.isOnline) {
                        onSync()
                        dashboardViewModel.refresh()
                    }
                },
                onSettingsClick = {
                    scope.launch { drawerState.close() }
                    onSettings()
                },
                currentRoute = "dashboard"
            )
        }
    ) {
        val customColors = LocalCustomColors.current
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = customColors.bgOffWhite
        ) { 
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
                        .padding(horizontal = 16.dp)
                ) {
                    val isWideScreen = maxWidth > 600.dp
                    val isRefreshing by dashboardViewModel.isRefreshing.collectAsState()
                    
                    AppPullToRefresh(
                        isRefreshing = isRefreshing,
                        onRefresh = dashboardViewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        ClinicLogo(isWideScreen)
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        DashboardMainGrid(
                            isWideScreen = isWideScreen,
                            uiState = uiState,
                            onPatientList = onPatientList,
                            onAddPatient = onAddPatient,
                            onTodayPatients = onTodayPatients,
                            dashboardAddConsultation = dashboardViewModel::addConsultation,
                            dashboardAddVaccination = dashboardViewModel::addVaccination,
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
                        
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    }
                }
            }
        }
    }
}
