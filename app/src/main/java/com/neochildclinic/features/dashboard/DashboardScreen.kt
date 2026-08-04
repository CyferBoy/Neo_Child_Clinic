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
import com.neochildclinic.domain.repository.SyncState
import com.neochildclinic.core.ui.AppBackground
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                // Drawer Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            scope.launch { drawerState.close() }
                            onProfile()
                        }
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = uiState.userName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = uiState.userRole,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Drawer Menu
                NavigationDrawerItem(
                    label = { Text("Dashboard") },
                    selected = true,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Dashboard, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                if (showManageStaff) {
                    NavigationDrawerItem(
                        label = { Text("Manage Staff") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onManageStaff()
                        },
                        icon = { Icon(Icons.Default.People, null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                if (showAuditLogs) {
                    NavigationDrawerItem(
                        label = { Text("Audit Log") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onAuditLogs()
                        },
                        icon = { Icon(Icons.Default.History, null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Drawer Footer
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        scope.launch { drawerState.close() }
                        onSettings()
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    
                    IconButton(onClick = {
                        scope.launch { drawerState.close() }
                        onSync()
                        dashboardViewModel.refresh()
                    }) {
                        val syncIcon = when (uiState.syncState) {
                            SyncState.SYNCING -> Icons.Default.Sync
                            SyncState.ERROR -> Icons.Default.CloudOff
                            else -> Icons.Default.CloudDone
                        }
                        Icon(syncIcon, contentDescription = "Cloud Sync")
                    }
                    
                    IconButton(onClick = {
                        scope.launch { drawerState.close() }
                        authViewModel.logout()
                        onLogout()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout, 
                            contentDescription = "Logout", 
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
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
