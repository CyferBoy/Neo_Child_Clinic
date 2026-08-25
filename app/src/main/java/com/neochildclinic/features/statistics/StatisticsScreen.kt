package com.neochildclinic.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Vaccination
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.data.local.entity.FinanceEntity
import com.neochildclinic.data.local.entity.ReminderEntity
import com.neochildclinic.core.designsystem.NeoChildTheme
import com.neochildclinic.core.ui.AppPullToRefresh
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    hasAccess: Boolean = false,
    onBack: () -> Unit = {},
    onMonthClick: (String) -> Unit = {}
) {
    if (!hasAccess) {
        StatisticsAccessDeniedScreen(onBack = onBack)
        return
    }

    val viewModel: StatisticsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.refreshError) { uiState.refreshError?.let { snackbarHostState.showSnackbar(it) } }

    StatisticsContent(
        uiState = uiState,
        drawerState = drawerState,
        onTabSelected = { tab ->
            viewModel.updateTab(tab)
            scope.launch { drawerState.close() }
        },
        onRefresh = viewModel::refresh,
        onMenuClick = { scope.launch { drawerState.open() } },
        onBack = onBack,
        onMonthClick = onMonthClick,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsAccessDeniedScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Access Denied",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Statistics are available only to administrators and doctors.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onBack) {
                    Text("Back to Dashboard")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsContent(
    uiState: StatisticsUiState,
    drawerState: DrawerState,
    onTabSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit,
    onBack: () -> Unit,
    onMonthClick: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val tabs = remember { listOf("Overview", "Patients", "Vaccinations", "Finance") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            StatisticsDrawerContent(
                tabs = tabs,
                selectedTab = uiState.selectedTab,
                onTabSelected = onTabSelected,
                onBack = onBack
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Statistics", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = tabs[uiState.selectedTab],
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Menu", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    StatisticsTabContent(
                        selectedTab = uiState.selectedTab,
                        patients = uiState.patients,
                        vaccinations = uiState.vaccinations,
                        inventory = uiState.inventory,
                        financeTransactions = uiState.financeTransactions,
                        vaccinationReminders = uiState.vaccinationReminders,
                        onMonthClick = onMonthClick
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticsDrawerContent(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    ModalDrawerSheet {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Statistics Menu",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))
        tabs.forEachIndexed { index, title ->
            NavigationDrawerItem(
                label = { Text(title) },
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = {
                    val icon = when (title) {
                        "Overview" -> Icons.Default.Dashboard
                        "Patients" -> Icons.Default.People
                        "Vaccinations" -> Icons.Default.Vaccines
                        "Finance" -> Icons.Default.Payments
                        else -> Icons.Default.BarChart
                    }
                    Icon(icon, contentDescription = null)
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        NavigationDrawerItem(
            label = { Text("Back to Dashboard") },
            selected = false,
            onClick = onBack,
            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "v${com.neochildclinic.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun StatisticsTabContent(
    selectedTab: Int,
    patients: List<Patient>,
    vaccinations: List<Vaccination>,
    inventory: List<InventoryItem>,
    financeTransactions: List<FinanceEntity>,
    vaccinationReminders: List<ReminderEntity>,
    onMonthClick: (String) -> Unit
) {
    when (selectedTab) {
        0 -> OverviewTab(patients, vaccinations, financeTransactions)
        1 -> PatientsTab(patients)
        2 -> VaccinationsTab(vaccinations, vaccinationReminders)
        3 -> FinanceTab(vaccinations, financeTransactions, onMonthClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun StatisticsPreview() {
    NeoChildTheme {
        StatisticsContent(
            uiState = StatisticsUiState(isLoading = false, selectedTab = 0),
            drawerState = rememberDrawerState(DrawerValue.Closed),
            onTabSelected = {},
            onMenuClick = {},
            onBack = {},
            onMonthClick = {},
            onRefresh = {},
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
