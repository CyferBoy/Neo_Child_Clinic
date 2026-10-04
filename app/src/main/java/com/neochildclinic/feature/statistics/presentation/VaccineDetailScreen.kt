package com.neochildclinic.feature.statistics.presentation

import com.neochildclinic.core.designsystem.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.neochildclinic.core.ui.AppBackground
import com.neochildclinic.core.ui.BackTopAppBar
import com.neochildclinic.core.ui.AppPullToRefresh
import com.neochildclinic.core.ui.SkeletonList
import com.neochildclinic.core.common.PatientUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccineDetailScreen(
    type: String,
    vaccineId: String?,
    onBack: () -> Unit,
    onPatientClick: (String) -> Unit,
    viewModel: VaccineDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Brand name is resolved from the catalog by id, so it may still be loading (or absent
    // for a catalog row since removed) - fall back to the vaccine type alone rather than
    // showing a blank or an id.
    val header = uiState.brandName?.let { "$type — $it" } ?: type

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                BackTopAppBar(
                    title = { Text(header) },
                    onBack = onBack
                )
            }
        ) { padding ->
            AppPullToRefresh(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                if (uiState.isLoading) {
                    SkeletonList(
                        modifier = Modifier.fillMaxSize(),
                        count = 8,
                        cardShaped = true,
                        spacing = Spacing.sm,
                        contentPadding = PaddingValues(Spacing.lg)
                    )
                } else if (uiState.entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No patients requiring this vaccine.",
                            modifier = Modifier.padding(Spacing.xl),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.entries, key = { "${it.reminder.id}_${it.brandName}" }) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    entry.patient?.id?.let { onPatientClick(it) }
                                },
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(Spacing.lg)) {
                                    Text(
                                        entry.patient?.name ?: "Unknown Patient",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    // Show the specific vaccine, not the reminder's whole
                                    // comma-joined list: when drilled into one brand, listing
                                    // every vaccine the patient is due would contradict the
                                    // filter that produced this row.
                                    Text(
                                        if (uiState.brandName == null) {
                                            "Vaccine: ${entry.brandName}"
                                        } else {
                                            "Vaccine type: $type"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Due: ${PatientUtils.formatDateForDisplay(entry.reminder.dueDate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
