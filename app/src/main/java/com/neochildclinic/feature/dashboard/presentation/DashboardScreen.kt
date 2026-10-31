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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    val sessionStatus by authViewModel.sessionStatus.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    if (isProfileLoading && authProfile == null) {
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
                        .padding(horizontal = Spacing.screen)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        SkeletonCard(
                            modifier = Modifier.fillMaxWidth(),
                            height = 56.dp,
                            shape = CardShape
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 196.dp,
                                    shape = CardShape
                                )
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 138.dp,
                                    shape = CardShape
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 150.dp,
                                    shape = CardShape
                                )
                                SkeletonCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 184.dp,
                                    shape = CardShape
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            repeat(3) {
                                SkeletonCard(
                                    modifier = Modifier.weight(1f),
                                    height = 84.dp,
                                    shape = CardShape
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

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
            dashboardViewModel.clearError()
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
                        "dashboard" -> {}
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
                    authViewModel.logout(onLogout)
                },
                onSyncClick = {
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) { 
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    DashboardTopBar(
                        onMenuClick = { scope.launch { drawerState.open() } },
                        userName = authProfile?.displayName,
                        onSearchClick = onSearch
                    )
                }
            ) { paddingValues ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = Spacing.screen)
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
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            Spacer(modifier = Modifier.height(Spacing.xs))

                            // Neo Child Clinic Brand & Online/Offline Status
                            DashboardBrandBar(isOnline = uiState.isOnline)

                            // Main 2x2 Asymmetric Grid (Patient List, Statistics, Today's Queue, Inventory)
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

                            // 3-Card Small Actions Row (Borrowed, Due, Waste)
                            DashboardSmallActionsRow(
                                uiState = uiState,
                                onBorrowed = onBorrowed,
                                onDue = onDue,
                                onWaste = onWaste
                            )

                            // Quick Actions Row (Patient, Vaccinate, Monthly stats, Expense)
                            DashboardQuickActionsRow(
                                onAddPatient = onAddPatient,
                                onVaccinate = onTodayPatients,
                                onMonthlyStats = onStatistics,
                                onExpense = onExpenses
                            )

                            // Footer Status & Version
                            DashboardFooter(
                                isOnline = uiState.isOnline,
                                pendingSyncCount = uiState.pendingSyncCount,
                                appVersion = com.neochildclinic.BuildConfig.VERSION_NAME
                            )

                            Spacer(modifier = Modifier.height(Spacing.xl))
                        }
                    }
                }
            }
        }
    }
}
