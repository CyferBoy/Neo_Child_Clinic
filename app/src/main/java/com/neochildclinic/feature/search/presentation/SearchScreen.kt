package com.neochildclinic.feature.search.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neochildclinic.core.common.PatientUtils.formatAgeYearsMonths
import com.neochildclinic.core.designsystem.*
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.EmptyState
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.SearchTopAppBar
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.domain.model.Patient

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPatientClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SearchTopAppBar(
                    title = "Search Patients",
                    searchQuery = query,
                    onSearchQueryChange = viewModel::onQueryChange,
                    isSearchActive = true,
                    onSearchActiveChange = { if (!it) onBack() },
                    onBack = onBack,
                    placeholder = "Name, Phone, ID or Vaccine..."
                )
            }
        ) { paddingValues ->
            AppPullToRefresh(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading && uiState.results.isEmpty()) {
                    SkeletonList(modifier = Modifier.fillMaxSize(), count = 8)
                } else if (uiState.results.isEmpty() && query.isNotBlank()) {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No results for \"$query\"",
                        message = "Check the spelling, or try a name, phone number or vaccine."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.screen),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        if (query.isNotBlank()) {
                            item {
                                Text(
                                    text = "${uiState.results.size} results for \"$query\"",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = Spacing.xs)
                                )
                            }
                        }

                        items(uiState.results, key = { it.id }) { patient ->
                            SearchResultItem(
                                patient = patient,
                                onClick = { onPatientClick(patient.id) }
                            )
                        }

                        item {
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                text = "Search also matches vaccine names and batch numbers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    patient: Patient,
    onClick: () -> Unit
) {
    val initials = remember(patient.name) {
        patient.name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifBlank { "P" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.card),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patient.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val clinicIdDisplay = if (patient.patientClinicId.isNullOrBlank() || patient.patientClinicId.startsWith("TEMP-")) "Not Assigned" else patient.patientClinicId
                val age = formatAgeYearsMonths(patient.dob)
                val subtitle = listOfNotNull(
                    age,
                    clinicIdDisplay,
                    patient.phone.takeIf { it.isNotBlank() }
                ).joinToString(" · ")

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
