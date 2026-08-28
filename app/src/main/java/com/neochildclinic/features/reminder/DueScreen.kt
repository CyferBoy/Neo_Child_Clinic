package com.neochildclinic.features.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.AppPullToRefresh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueScreen(
    onBack: () -> Unit,
    onNavigateToCompletedDismissed: () -> Unit,
    onPatientClick: (String) -> Unit,
    viewModel: DueViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }

    AppBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = { Text("Search patient...") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Due Vaccinations")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = if (isSearchActive) { { isSearchActive = false; viewModel.updateSearchQuery("") } } else onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    DueTab(
                        patients = uiState.patients, 
                        filteredVaccinations = uiState.filteredVaccinations,
                        overdueCount = uiState.overdueCount,
                        stats = stats,
                        initialFilter = uiState.selectedFilter,
                        onFilterChanged = viewModel::updateFilter,
                        onSearchQueryChanged = viewModel::updateSearchQuery,
                        onComplete = viewModel::completeVaccination,
                        onDismissReminder = viewModel::dismissReminder,
                        onReschedule = viewModel::rescheduleVaccination,
                        onNavigateToCompletedDismissed = onNavigateToCompletedDismissed,
                        onPatientClick = onPatientClick
                    )
                }
            }
        }
    }
}
