package com.neochildclinic.feature.statistics.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.SkeletonCard
import com.neochildclinic.domain.model.Expense
import com.neochildclinic.domain.model.FinanceTransaction
import com.neochildclinic.domain.model.InventoryItem
import com.neochildclinic.domain.model.Patient
import com.neochildclinic.domain.model.Reminder
import com.neochildclinic.domain.model.Vaccination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    hasAccess: Boolean = false,
    onBack: () -> Unit = {},
    onMonthClick: (String) -> Unit = {},
    onMilestoneClick: (String) -> Unit = {},
    onFullReportClick: () -> Unit = {},
    onVaccineTypeClick: (String, String?) -> Unit = { _, _ -> }
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
        onMilestoneClick = onMilestoneClick,
        onFullReportClick = onFullReportClick,
        onVaccineTypeClick = onVaccineTypeClick,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsAccessDeniedScreen(onBack: () -> Unit) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text("Statistics", fontWeight = FontWeight.SemiBold) },
                    onBack = onBack
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(Spacing.screen),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "Statistics access",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Statistics are available only to administrators and doctors.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Button(
                        onClick = onBack,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.heightIn(min = Spacing.touchTarget)
                    ) {
                        Text("Back to dashboard")
                    }
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
    onMilestoneClick: (String) -> Unit,
    onFullReportClick: () -> Unit,
    onVaccineTypeClick: (String, String?) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val tabs = listOf("Overview", "Patients", "Vaccinations", "Finance", "Map")

    AppBackground {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            topBar = {
                Column {
                    BackTopAppBar(
                        title = {
                            Text(
                                text = "Statistics",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        onBack = onBack,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    StatisticsTabBar(
                        tabs = tabs,
                        selectedIndex = uiState.selectedTab,
                        onTabSelected = onTabSelected
                    )
                }
            },
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SkeletonCard(modifier = Modifier.weight(1f), height = 48.dp)
                            SkeletonCard(modifier = Modifier.weight(0.85f), height = 48.dp)
                            SkeletonCard(modifier = Modifier.weight(0.9f), height = 48.dp)
                        }
                        Spacer(modifier = Modifier.height(Spacing.xl))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            SkeletonCard(modifier = Modifier.weight(1f), height = 108.dp)
                            SkeletonCard(modifier = Modifier.weight(1f), height = 108.dp)
                        }
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            SkeletonCard(modifier = Modifier.weight(1f), height = 108.dp)
                            SkeletonCard(modifier = Modifier.weight(1f), height = 108.dp)
                        }
                        Spacer(modifier = Modifier.height(Spacing.xl))
                        SkeletonCard(modifier = Modifier.fillMaxWidth(), height = 240.dp)
                    }
                } else {
                    StatisticsTabContent(
                        selectedTab = uiState.selectedTab,
                        patients = uiState.patients,
                        vaccinations = uiState.vaccinations,
                        inventory = uiState.inventory,
                        financeTransactions = uiState.financeTransactions,
                        vaccinationReminders = uiState.vaccinationReminders,
                        expenses = uiState.expenses,
                        onMonthClick = onMonthClick,
                        onMilestoneClick = onMilestoneClick,
                        onFullReportClick = onFullReportClick,
                        onVaccineTypeClick = onVaccineTypeClick
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticsTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.sm)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            val weight = when {
                title.length <= 3 -> 0.75f
                title.length <= 7 -> 0.9f
                else -> 1.15f
            }
            Box(
                modifier = Modifier
                    .weight(weight)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (selected) {
                            Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        } else {
                            Modifier
                        }
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onTabSelected(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun StatisticsTabContent(
    selectedTab: Int,
    patients: List<Patient>,
    vaccinations: List<Vaccination>,
    inventory: List<InventoryItem>,
    financeTransactions: List<FinanceTransaction>,
    vaccinationReminders: List<Reminder>,
    expenses: List<Expense>,
    onMonthClick: (String) -> Unit,
    onMilestoneClick: (String) -> Unit,
    onFullReportClick: () -> Unit,
    onVaccineTypeClick: (String, String?) -> Unit
) {
    when (selectedTab) {
        0 -> OverviewTab(patients, vaccinations, financeTransactions, onFullReportClick = onFullReportClick)
        1 -> PatientsTab(patients, vaccinations, onMilestoneClick)
        2 -> VaccinationsTab(vaccinations, vaccinationReminders, inventory, onVaccineTypeClick = onVaccineTypeClick)
        3 -> FinanceTab(vaccinations, financeTransactions, expenses, onMonthClick)
        4 -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.screen),
            contentAlignment = Alignment.Center
        ) {
            StatisticsEmptyState(
                icon = Icons.Default.Map,
                title = "Map coming soon",
                message = "Clinic location insights will appear here in a future update."
            )
        }
    }
}
