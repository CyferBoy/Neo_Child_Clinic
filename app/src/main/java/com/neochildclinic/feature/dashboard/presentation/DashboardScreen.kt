package com.neochildclinic.feature.dashboard.presentation

import com.neochildclinic.core.designsystem.*

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
import com.neochildclinic.core.security.AuthViewModel
import com.neochildclinic.domain.model.SyncState
import com.neochildclinic.core.ui.SkeletonBox
import com.neochildclinic.core.ui.SkeletonCard
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.feature.dashboard.presentation.component.AppDrawer
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
    // Invalidate this scope when the session (and app_metadata.role) changes —
    // the role getter below is a plain read, so this collect is the only
    // recomposition trigger for it.
    val sessionStatus by authViewModel.sessionStatus.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Hold the skeleton until the profile *display* data (name, etc.) has resolved;
    // the authorization role no longer comes from it - see `role` below, sourced from
    // the session's app_metadata instead of the old profile.role nurse-default.
    if (isProfileLoading && authProfile == null) {
        // Skeleton shown while the profile (and therefore the real dashboard content) is
        // still resolving - see the comment above for why this gate exists at all. It renders
        // inside the same shell (background + top bar) and mirrors the real dashboard layout:
        // logo header, the 2-column tile grid (210/150 and 150/210 heights, 16dp gaps), then
        // the 3-tile Borrowed/Due/Waste row - so the loading state reads as the dashboard.
        val customColors = LocalCustomColors.current
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = { DashboardTopBar(onMenuClick = {}) }
            ) { paddingValues ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = Spacing.lg)
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
                                shape = MaterialTheme.shapes.large
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // DashboardMainGrid placeholder: same 2 columns, same tile heights
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                            ) {
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 210.dp,
                                    shape = ChartContainerShape
                                )
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 150.dp,
                                    shape = ChartContainerShape
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                            ) {
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 150.dp,
                                    shape = ChartContainerShape
                                )
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 210.dp,
                                    shape = ChartContainerShape
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.lg))

                        // DashboardSmallActionsRow placeholder (Borrowed / Due / Waste)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            repeat(3) {
                                SkeletonCard(
                                    modifier = Modifier.weight(1f),
                                    height = 90.dp,
                                    shape = RoundedCornerShape(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(Spacing.xl))
                    }
                }
            }
        }
        return
    }

    // Authorization role: session app_metadata (via AuthViewModel), never profile.role.
    // The `sessionStatus.let` is a deliberate State read: collecting alone does not
    // subscribe this recompose scope — reading the value here is what re-runs this
    // line when the session (and app_metadata.role) changes.
    val role = sessionStatus.let { authViewModel.currentUserRole }

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
            color = MaterialTheme.colorScheme.background
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
                        .padding(horizontal = Spacing.lg)
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
                        
                        Spacer(modifier = Modifier.height(Spacing.sm))

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

                        Spacer(modifier = Modifier.height(Spacing.lg))

                        DashboardSmallActionsRow(
                            uiState = uiState,
                            onBorrowed = onBorrowed,
                            onDue = onDue,
                            onWaste = onWaste
                        )
                        
                        Spacer(modifier = Modifier.height(Spacing.xl))
                    }
                    }
                }
            }
        }
    }
}
