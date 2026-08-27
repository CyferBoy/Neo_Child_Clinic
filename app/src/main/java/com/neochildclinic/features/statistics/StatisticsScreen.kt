package com.neochildclinic.features.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState.refreshError) { 
        uiState.refreshError?.let { snackbarHostState.showSnackbar(it) } 
    }

    StatisticsContent(
        uiState = uiState,
        onTabSelected = { viewModel.updateTab(it) },
        onRefresh = viewModel::refresh,
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
    onTabSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onMonthClick: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val tabs = listOf(
        TabItem("Overview", Icons.Default.PieChart),
        TabItem("Patients", Icons.Default.People),
        TabItem("Vaccinations", Icons.Default.Vaccines),
        TabItem("Finance", Icons.Default.BusinessCenter),
        TabItem("Map", Icons.Default.Map)
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Statistics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                
                // Horizontal Tab Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    tabs.forEachIndexed { index, tab ->
                        TabIndicator(
                            modifier = Modifier.weight(1f),
                            title = tab.title,
                            icon = tab.icon,
                            isSelected = uiState.selectedTab == index,
                            onClick = { onTabSelected(index) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
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

@Composable
private fun TabIndicator(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .selectable(selected = isSelected, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

private data class TabItem(val title: String, val icon: ImageVector)

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
        4 -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Map coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
